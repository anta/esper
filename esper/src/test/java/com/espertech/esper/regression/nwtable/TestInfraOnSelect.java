/*
 * *************************************************************************************
 *  Copyright (C) 2006-2015 EsperTech, Inc. All rights reserved.                       *
 *  http://www.espertech.com/esper                                                     *
 *  http://www.espertech.com                                                           *
 *  ---------------------------------------------------------------------------------- *
 *  The software in this package is published under the terms of the GPL license       *
 *  a copy of which has been included with this distribution in the license.txt file.  *
 * *************************************************************************************
 */

package com.espertech.esper.regression.nwtable;

import com.espertech.esper.client.*;
import com.espertech.esper.client.scopetest.EPAssertionUtil;
import com.espertech.esper.client.scopetest.SupportUpdateListener;
import com.espertech.esper.client.time.CurrentTimeEvent;
import com.espertech.esper.core.service.EPStatementSPI;
import com.espertech.esper.core.service.StatementType;
import com.espertech.esper.metrics.instrumentation.InstrumentationHelper;
import com.espertech.esper.support.bean.*;
import com.espertech.esper.support.client.SupportConfigFactory;
import com.espertech.esper.support.epl.SupportQueryPlanIndexHook;
import com.espertech.esper.support.util.IndexAssertion;
import com.espertech.esper.support.util.IndexAssertionEventSend;
import com.espertech.esper.support.util.IndexBackingTableInfo;
import com.espertech.esper.support.util.SupportMessageAssertUtil;
import junit.framework.TestCase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Collection;
import java.util.Map;

public class TestInfraOnSelect extends TestCase implements IndexBackingTableInfo
{
    private final static Log log = LogFactory.getLog(TestInfraOnSelect.class);

    private EPServiceProvider epService;
    private SupportUpdateListener listenerSelect;

    public void setUp()
    {
        Configuration config = SupportConfigFactory.getConfiguration();
        config.getEngineDefaults().getLogging().setEnableQueryPlan(true);
        epService = EPServiceProviderManager.getDefaultProvider(config);
        epService.initialize();
        if (InstrumentationHelper.ENABLED) { InstrumentationHelper.startTest(epService, this.getClass(), getName());}
        listenerSelect = new SupportUpdateListener();
        SupportQueryPlanIndexHook.reset();
    }

    protected void tearDown() throws Exception {
        if (InstrumentationHelper.ENABLED) { InstrumentationHelper.endTest();}
        listenerSelect = null;
    }

    public void testOnSelectIndexChoice() {
        runAssertionOnSelectIndexChoice(true);
        runAssertionOnSelectIndexChoice(false);
    }

    public void testWindowAgg() {
        epService.getEPAdministrator().getConfiguration().addEventType("SupportBean", SupportBean.class);
        epService.getEPAdministrator().getConfiguration().addEventType("S0", SupportBean_S0.class);
        epService.getEPAdministrator().getConfiguration().addEventType("S1", SupportBean_S1.class);

        runAssertionWindowAgg(true);
        runAssertionWindowAgg(false);
    }

    public void testSelectAggregationHavingStreamWildcard() {
        epService.getEPAdministrator().getConfiguration().addEventType("SupportBean", SupportBean.class);
        epService.getEPAdministrator().getConfiguration().addEventType("SupportBean_A", SupportBean_A.class);

        runAssertionSelectAggregationHavingStreamWildcard(true);
        runAssertionSelectAggregationHavingStreamWildcard(false);
    }

    public void testPatternTimedSelectNW() {
        runAssertionPatternTimedSelect(true);
    }

    public void testPatternTimedSelectTable() {
        runAssertionPatternTimedSelect(false);
    }

    public void testInvalid() {
        runAssertionInvalid(true);
        runAssertionInvalid(false);
    }

    public void testSelectCondition() {
        runAssertionSelectCondition(true);
        runAssertionSelectCondition(false);
    }

    public void testSelectJoinColumnsLimit() {
        runAssertionSelectJoinColumnsLimit(true);
        runAssertionSelectJoinColumnsLimit(false);
    }

    public void testSelectAggregation() {
        runAssertionSelectAggregation(true);
        runAssertionSelectAggregation(false);
    }

    public void testSelectAggregationCorrelated() {
        runAssertionSelectAggregationCorrelated(true);
        runAssertionSelectAggregationCorrelated(false);
    }

    public void testSelectAggregationGrouping() {
        runAssertionSelectAggregationGrouping(true);
        runAssertionSelectAggregationGrouping(false);
    }

    public void testSelectCorrelationDelete() {
        runAssertionSelectCorrelationDelete(true);
        runAssertionSelectCorrelationDelete(false);
    }

    public void testPatternCorrelation() {
        runAssertionPatternCorrelation(true);
        runAssertionPatternCorrelation(false);
    }

    private void runAssertionPatternCorrelation(boolean namedWindow)
    {
        String[] fields = new String[] {"a", "b"};

        // create window
        String stmtTextCreate = namedWindow ?
                "create window MyInfra.win:keepall() as select theString as a, intPrimitive as b from " + SupportBean.class.getName() :
                "create table MyInfra(a string primary key, b int primary key)";
        EPStatement stmtCreate = epService.getEPAdministrator().createEPL(stmtTextCreate);

        // create select stmt
        String stmtTextSelect = "on pattern [every ea=" + SupportBean_A.class.getName() +
                " or every eb=" + SupportBean_B.class.getName() + "] select mywin.* from MyInfra as mywin where a = coalesce(ea.id, eb.id)";
        EPStatement stmtSelect = epService.getEPAdministrator().createEPL(stmtTextSelect);
        stmtSelect.addListener(listenerSelect);

        // create insert into
        String stmtTextInsertOne = "insert into MyInfra select theString as a, intPrimitive as b from " + SupportBean.class.getName();
        epService.getEPAdministrator().createEPL(stmtTextInsertOne);

        // send 3 event
        sendSupportBean("E1", 1);
        sendSupportBean("E2", 2);
        sendSupportBean("E3", 3);
        assertFalse(listenerSelect.isInvoked());

        // fire trigger
        sendSupportBean_A("X1");
        assertFalse(listenerSelect.isInvoked());
        EPAssertionUtil.assertPropsPerRowAnyOrder(stmtCreate.iterator(), fields, new Object[][]{{"E1", 1}, {"E2", 2}, {"E3", 3}});
        if (namedWindow) {
            EPAssertionUtil.assertPropsPerRow(stmtSelect.iterator(), fields, null);
        }

        sendSupportBean_B("E2");
        EPAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fields, new Object[]{"E2", 2});
        if (namedWindow) {
            EPAssertionUtil.assertPropsPerRow(stmtSelect.iterator(), fields, new Object[][]{{"E2", 2}});
        }

        sendSupportBean_A("E1");
        EPAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fields, new Object[]{"E1", 1});
        if (namedWindow) {
            EPAssertionUtil.assertPropsPerRow(stmtSelect.iterator(), fields, new Object[][]{{"E1", 1}});
        }

        sendSupportBean_B("E3");
        EPAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fields, new Object[]{"E3", 3});
        if (namedWindow) {
            EPAssertionUtil.assertPropsPerRow(stmtSelect.iterator(), fields, new Object[][]{{"E3", 3}});
        }
        EPAssertionUtil.assertPropsPerRowAnyOrder(stmtCreate.iterator(), fields, new Object[][]{{"E1", 1}, {"E2", 2}, {"E3", 3}});

        stmtCreate.destroy();
        stmtSelect.destroy();
        epService.getEPAdministrator().destroyAllStatements();
        epService.getEPAdministrator().getConfiguration().removeEventType("MyInfra", false);
    }

    private void runAssertionSelectCorrelationDelete(boolean namedWindow)
    {
        String[] fields = new String[] {"a", "b"};

        // create window
        String stmtTextCreate = namedWindow ?
                "create window MyInfra.win:keepall() as select theString as a, intPrimitive as b from " + SupportBean.class.getName() :
                "create table MyInfra(a string primary key, b int primary key)";
        EPStatement stmtCreate = epService.getEPAdministrator().createEPL(stmtTextCreate);

        // create select stmt
        String stmtTextSelect = "on " + SupportBean_A.class.getName() + " select mywin.* from MyInfra as mywin where id = a";
        EPStatement stmtSelect = epService.getEPAdministrator().createEPL(stmtTextSelect);
        stmtSelect.addListener(listenerSelect);

        // create insert into
        String stmtTextInsertOne = "insert into MyInfra select theString as a, intPrimitive as b from " + SupportBean.class.getName();
        epService.getEPAdministrator().createEPL(stmtTextInsertOne);

        // create delete stmt
        String stmtTextDelete = "on " + SupportBean_B.class.getName() + " delete from MyInfra where a = id";
        EPStatement stmtDelete = epService.getEPAdministrator().createEPL(stmtTextDelete);

        // send 3 event
        sendSupportBean("E1", 1);
        sendSupportBean("E2", 2);
        sendSupportBean("E3", 3);
        assertFalse(listenerSelect.isInvoked());

        // fire trigger
        sendSupportBean_A("X1");
        assertFalse(listenerSelect.isInvoked());
        EPAssertionUtil.assertPropsPerRowAnyOrder(stmtCreate.iterator(), fields, new Object[][]{{"E1", 1}, {"E2", 2}, {"E3", 3}});
        if (namedWindow) {
            EPAssertionUtil.assertPropsPerRow(stmtSelect.iterator(), fields, null);
        }

        sendSupportBean_A("E2");
        EPAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fields, new Object[]{"E2", 2});
        EPAssertionUtil.assertPropsPerRowAnyOrder(stmtCreate.iterator(), fields, new Object[][]{{"E1", 1}, {"E2", 2}, {"E3", 3}});
        if (namedWindow) {
            EPAssertionUtil.assertPropsPerRowAnyOrder(stmtSelect.iterator(), fields, new Object[][]{{"E2", 2}});
        }

        sendSupportBean_A("E1");
        EPAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fields, new Object[]{"E1", 1});
        if (namedWindow) {
            EPAssertionUtil.assertPropsPerRow(stmtSelect.iterator(), fields, new Object[][]{{"E1", 1}});
        }
        EPAssertionUtil.assertPropsPerRowAnyOrder(stmtCreate.iterator(), fields, new Object[][]{{"E1", 1}, {"E2", 2}, {"E3", 3}});

        // delete event
        sendSupportBean_B("E1");
        assertFalse(listenerSelect.isInvoked());

        sendSupportBean_A("E1");
        assertFalse(listenerSelect.isInvoked());
        EPAssertionUtil.assertPropsPerRowAnyOrder(stmtCreate.iterator(), fields, new Object[][]{{"E2", 2}, {"E3", 3}});
        if (namedWindow) {
            EPAssertionUtil.assertPropsPerRow(stmtSelect.iterator(), fields, null);
        }

        sendSupportBean_A("E2");
        EPAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fields, new Object[]{"E2", 2});
        if (namedWindow) {
            EPAssertionUtil.assertPropsPerRow(stmtSelect.iterator(), fields, new Object[][]{{"E2", 2}});
        }

        stmtSelect.destroy();
        stmtDelete.destroy();
        stmtCreate.destroy();
        epService.getEPAdministrator().destroyAllStatements();
        epService.getEPAdministrator().getConfiguration().removeEventType("MyInfra", false);
    }

    private void runAssertionSelectAggregationGrouping(boolean namedWindow)
    {
        String[] fields = new String[] {"a", "sumb"};
        SupportUpdateListener listenerSelectTwo = new SupportUpdateListener();

        // create window
        String stmtTextCreate = namedWindow ?
                "create window MyInfra.win:keepall() as select theString as a, intPrimitive as b from " + SupportBean.class.getName() :
                "create table MyInfra(a string primary key, b int primary key)";
        EPStatement stmtCreate = epService.getEPAdministrator().createEPL(stmtTextCreate);

        // create select stmt
        String stmtTextSelect = "on " + SupportBean_A.class.getName() + " select a, sum(b) as sumb from MyInfra group by a order by a desc";
        EPStatement stmtSelect = epService.getEPAdministrator().createEPL(stmtTextSelect);
        stmtSelect.addListener(listenerSelect);

        // create select stmt
        String stmtTextSelectTwo = "on " + SupportBean_A.class.getName() + " select a, sum(b) as sumb from MyInfra group by a having sum(b) > 5 order by a desc";
        EPStatement stmtSelectTwo = epService.getEPAdministrator().createEPL(stmtTextSelectTwo);
        stmtSelectTwo.addListener(listenerSelectTwo);

        // create insert into
        String stmtTextInsertOne = "insert into MyInfra select theString as a, intPrimitive as b from " + SupportBean.class.getName();
        epService.getEPAdministrator().createEPL(stmtTextInsertOne);

        // fire trigger
        sendSupportBean_A("A1");
        assertFalse(listenerSelect.isInvoked());
        assertFalse(listenerSelectTwo.isInvoked());

        // send 3 events
        sendSupportBean("E1", 1);
        sendSupportBean("E2", 2);
        sendSupportBean("E1", 5);
        assertFalse(listenerSelect.isInvoked());
        assertFalse(listenerSelectTwo.isInvoked());

        // fire trigger
        sendSupportBean_A("A1");
        EPAssertionUtil.assertPropsPerRow(listenerSelect.getLastNewData(), fields, new Object[][]{{"E2", 2}, {"E1", 6}});
        assertNull(listenerSelect.getLastOldData());
        listenerSelect.reset();
        EPAssertionUtil.assertPropsPerRow(listenerSelectTwo.getLastNewData(), fields, new Object[][]{{"E1", 6}});
        assertNull(listenerSelect.getLastOldData());
        listenerSelect.reset();

        // send 3 events
        sendSupportBean("E4", -1);
        sendSupportBean("E2", 10);
        sendSupportBean("E1", 100);
        assertFalse(listenerSelect.isInvoked());

        sendSupportBean_A("A2");
        EPAssertionUtil.assertPropsPerRow(listenerSelect.getLastNewData(), fields, new Object[][]{{"E4", -1}, {"E2", 12}, {"E1", 106}});

        // create delete stmt, delete E2
        String stmtTextDelete = "on " + SupportBean_B.class.getName() + " delete from MyInfra where id = a";
        epService.getEPAdministrator().createEPL(stmtTextDelete);
        sendSupportBean_B("E2");

        sendSupportBean_A("A3");
        EPAssertionUtil.assertPropsPerRow(listenerSelect.getLastNewData(), fields, new Object[][]{{"E4", -1}, {"E1", 106}});
        assertNull(listenerSelect.getLastOldData());
        listenerSelect.reset();
        EPAssertionUtil.assertPropsPerRow(listenerSelectTwo.getLastNewData(), fields, new Object[][]{{"E1", 106}});
        assertNull(listenerSelectTwo.getLastOldData());
        listenerSelectTwo.reset();

        EventType resultType = stmtSelect.getEventType();
        assertEquals(2, resultType.getPropertyNames().length);
        assertEquals(String.class, resultType.getPropertyType("a"));
        assertEquals(Integer.class, resultType.getPropertyType("sumb"));

        stmtSelect.destroy();
        stmtCreate.destroy();
        stmtSelectTwo.destroy();
        epService.getEPAdministrator().destroyAllStatements();
        epService.getEPAdministrator().getConfiguration().removeEventType("MyInfra", false);
    }

    private void runAssertionSelectAggregationCorrelated(boolean namedWindow)
    {
        String[] fields = new String[] {"sumb"};

        // create window
        String stmtTextCreate = namedWindow ?
                "create window MyInfra.win:keepall() as select theString as a, intPrimitive as b from " + SupportBean.class.getName() :
                "create table MyInfra(a string primary key, b int primary key)";
        EPStatement stmtCreate = epService.getEPAdministrator().createEPL(stmtTextCreate);

        // create select stmt
        String stmtTextSelect = "on " + SupportBean_A.class.getName() + " select sum(b) as sumb from MyInfra where a = id";
        EPStatement stmtSelect = epService.getEPAdministrator().createEPL(stmtTextSelect);
        stmtSelect.addListener(listenerSelect);

        // create insert into
        String stmtTextInsertOne = "insert into MyInfra select theString as a, intPrimitive as b from " + SupportBean.class.getName();
        epService.getEPAdministrator().createEPL(stmtTextInsertOne);

        // send 3 event
        sendSupportBean("E1", 1);
        sendSupportBean("E2", 2);
        sendSupportBean("E3", 3);
        assertFalse(listenerSelect.isInvoked());

        // fire trigger
        sendSupportBean_A("A1");
        EPAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fields, new Object[]{null});
        if (namedWindow) {
            EPAssertionUtil.assertPropsPerRow(stmtSelect.iterator(), fields, new Object[][]{{null}});
        }

        // fire trigger
        sendSupportBean_A("E2");
        EPAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fields, new Object[]{2});
        if (namedWindow) {
            EPAssertionUtil.assertPropsPerRow(stmtSelect.iterator(), fields, new Object[][]{{2}});
        }

        sendSupportBean("E2", 10);
        sendSupportBean_A("E2");
        EPAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fields, new Object[]{12});
        if (namedWindow) {
            EPAssertionUtil.assertPropsPerRow(stmtSelect.iterator(), fields, new Object[][]{{12}});
        }

        EventType resultType = stmtSelect.getEventType();
        assertEquals(1, resultType.getPropertyNames().length);
        assertEquals(Integer.class, resultType.getPropertyType("sumb"));

        stmtSelect.destroy();
        stmtCreate.destroy();
        epService.getEPAdministrator().destroyAllStatements();
        epService.getEPAdministrator().getConfiguration().removeEventType("MyInfra", false);
    }

    private void runAssertionSelectAggregation(boolean namedWindow)
    {
        String[] fields = new String[] {"sumb"};

        // create window
        String stmtTextCreate = namedWindow ?
                "create window MyInfra.win:keepall() as select theString as a, intPrimitive as b from " + SupportBean.class.getName() :
                "create table MyInfra (a string primary key, b int primary key)";
        EPStatement stmtCreate = epService.getEPAdministrator().createEPL(stmtTextCreate);

        // create select stmt
        String stmtTextSelect = "on " + SupportBean_A.class.getName() + " select sum(b) as sumb from MyInfra";
        EPStatement stmtSelect = epService.getEPAdministrator().createEPL(stmtTextSelect);
        stmtSelect.addListener(listenerSelect);

        // create insert into
        String stmtTextInsertOne = "insert into MyInfra select theString as a, intPrimitive as b from " + SupportBean.class.getName();
        epService.getEPAdministrator().createEPL(stmtTextInsertOne);

        // send 3 event
        sendSupportBean("E1", 1);
        sendSupportBean("E2", 2);
        sendSupportBean("E3", 3);
        assertFalse(listenerSelect.isInvoked());

        // fire trigger
        sendSupportBean_A("A1");
        EPAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fields, new Object[]{6});
        if (namedWindow) {
            EPAssertionUtil.assertPropsPerRow(stmtSelect.iterator(), fields, new Object[][]{{6}});
        }

        // create delete stmt
        String stmtTextDelete = "on " + SupportBean_B.class.getName() + " delete from MyInfra where id = a";
        epService.getEPAdministrator().createEPL(stmtTextDelete);

        // Delete E2
        sendSupportBean_B("E2");

        // fire trigger
        sendSupportBean_A("A2");
        EPAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fields, new Object[]{4});
        if (namedWindow) {
            EPAssertionUtil.assertPropsPerRow(stmtSelect.iterator(), fields, new Object[][]{{4}});
        }

        sendSupportBean("E4", 10);
        sendSupportBean_A("A3");
        EPAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fields, new Object[]{14});
        if (namedWindow) {
            EPAssertionUtil.assertPropsPerRow(stmtSelect.iterator(), fields, new Object[][]{{14}});
        }

        EventType resultType = stmtSelect.getEventType();
        assertEquals(1, resultType.getPropertyNames().length);
        assertEquals(Integer.class, resultType.getPropertyType("sumb"));

        stmtSelect.destroy();
        stmtCreate.destroy();
        epService.getEPAdministrator().destroyAllStatements();
        epService.getEPAdministrator().getConfiguration().removeEventType("MyInfra", false);
    }

    private void runAssertionSelectJoinColumnsLimit(boolean namedWindow)
    {
        String[] fields = new String[] {"triggerid", "wina", "b"};

        // create window
        String stmtTextCreate = namedWindow ?
                "create window MyInfra.win:keepall() as select theString as a, intPrimitive as b from " + SupportBean.class.getName() :
                "create table MyInfra (a string primary key, b int)";
        EPStatement stmtCreate = epService.getEPAdministrator().createEPL(stmtTextCreate);

        // create select stmt
        String stmtTextSelect = "on " + SupportBean_A.class.getName() + " as trigger select trigger.id as triggerid, win.a as wina, b from MyInfra as win order by wina";
        EPStatement stmtSelect = epService.getEPAdministrator().createEPL(stmtTextSelect);
        stmtSelect.addListener(listenerSelect);

        // create insert into
        String stmtTextInsertOne = "insert into MyInfra select theString as a, intPrimitive as b from " + SupportBean.class.getName();
        epService.getEPAdministrator().createEPL(stmtTextInsertOne);

        // send 3 event
        sendSupportBean("E1", 1);
        sendSupportBean("E2", 2);
        assertFalse(listenerSelect.isInvoked());

        // fire trigger
        sendSupportBean_A("A1");
        assertEquals(2, listenerSelect.getLastNewData().length);
        EPAssertionUtil.assertProps(listenerSelect.getLastNewData()[0], fields, new Object[]{"A1", "E1", 1});
        EPAssertionUtil.assertProps(listenerSelect.getLastNewData()[1], fields, new Object[]{"A1", "E2", 2});
        if (namedWindow) {
            EPAssertionUtil.assertPropsPerRow(stmtSelect.iterator(), fields, new Object[][]{{"A1", "E1", 1}, {"A1", "E2", 2}});
        }

        // try limit clause
        stmtSelect.destroy();
        stmtTextSelect = "on " + SupportBean_A.class.getName() + " as trigger select trigger.id as triggerid, win.a as wina, b from MyInfra as win order by wina limit 1";
        stmtSelect = epService.getEPAdministrator().createEPL(stmtTextSelect);
        stmtSelect.addListener(listenerSelect);

        sendSupportBean_A("A1");
        assertEquals(1, listenerSelect.getLastNewData().length);
        EPAssertionUtil.assertProps(listenerSelect.getLastNewData()[0], fields, new Object[]{"A1", "E1", 1});
        if (namedWindow) {
            EPAssertionUtil.assertPropsPerRow(stmtSelect.iterator(), fields, new Object[][]{{"A1", "E1", 1}});
        }

        stmtCreate.destroy();
        listenerSelect.reset();
        epService.getEPAdministrator().destroyAllStatements();
        epService.getEPAdministrator().getConfiguration().removeEventType("MyInfra", false);
    }

    private void runAssertionSelectCondition(boolean namedWindow)
    {
        String[] fieldsCreate = new String[] {"a", "b"};
        String[] fieldsOnSelect = new String[] {"a", "b", "id"};

        // create window
        String stmtTextCreate = namedWindow ?
                "create window MyInfra.win:keepall() as select theString as a, intPrimitive as b from " + SupportBean.class.getName() :
                "create table MyInfra (a string primary key, b int)";
        EPStatement stmtCreate = epService.getEPAdministrator().createEPL(stmtTextCreate);

        // create select stmt
        String stmtTextSelect = "on " + SupportBean_A.class.getName() + " select mywin.*, id from MyInfra as mywin where MyInfra.b < 3 order by a asc";
        EPStatement stmtSelect = epService.getEPAdministrator().createEPL(stmtTextSelect);
        stmtSelect.addListener(listenerSelect);
        assertEquals(StatementType.ON_SELECT, ((EPStatementSPI) stmtSelect).getStatementMetadata().getStatementType());

        // create insert into
        String stmtTextInsertOne = "insert into MyInfra select theString as a, intPrimitive as b from " + SupportBean.class.getName();
        epService.getEPAdministrator().createEPL(stmtTextInsertOne);

        // send 3 event
        sendSupportBean("E1", 1);
        sendSupportBean("E2", 2);
        sendSupportBean("E3", 3);
        assertFalse(listenerSelect.isInvoked());

        // fire trigger
        sendSupportBean_A("A1");
        assertEquals(2, listenerSelect.getLastNewData().length);
        EPAssertionUtil.assertProps(listenerSelect.getLastNewData()[0], fieldsCreate, new Object[]{"E1", 1});
        EPAssertionUtil.assertProps(listenerSelect.getLastNewData()[1], fieldsCreate, new Object[]{"E2", 2});
        EPAssertionUtil.assertPropsPerRowAnyOrder(stmtCreate.iterator(), fieldsCreate, new Object[][]{{"E1", 1}, {"E2", 2}, {"E3", 3}});
        if (namedWindow) {
            EPAssertionUtil.assertPropsPerRow(stmtSelect.iterator(), fieldsOnSelect, new Object[][]{{"E1", 1, "A1"}, {"E2", 2, "A1"}});
        }
        else {
            assertFalse(stmtSelect.iterator().hasNext());
        }

        sendSupportBean("E4", 0);
        sendSupportBean_A("A2");
        assertEquals(3, listenerSelect.getLastNewData().length);
        EPAssertionUtil.assertProps(listenerSelect.getLastNewData()[0], fieldsOnSelect, new Object[]{"E1", 1, "A2"});
        EPAssertionUtil.assertProps(listenerSelect.getLastNewData()[1], fieldsOnSelect, new Object[]{"E2", 2, "A2"});
        EPAssertionUtil.assertProps(listenerSelect.getLastNewData()[2], fieldsOnSelect, new Object[]{"E4", 0, "A2"});
        EPAssertionUtil.assertPropsPerRowAnyOrder(stmtCreate.iterator(), fieldsCreate, new Object[][]{{"E1", 1}, {"E2", 2}, {"E3", 3}, {"E4", 0}});
        if (namedWindow) {
            EPAssertionUtil.assertPropsPerRow(stmtSelect.iterator(), fieldsCreate, new Object[][]{{"E1", 1}, {"E2", 2}, {"E4", 0}});
        }

        stmtSelect.destroy();
        stmtCreate.destroy();
        listenerSelect.reset();
        epService.getEPAdministrator().destroyAllStatements();
        epService.getEPAdministrator().getConfiguration().removeEventType("MyInfra", false);
    }

    private void runAssertionInvalid(boolean namedWindow) {
        String stmtTextCreate = namedWindow ?
                "create window MyInfra.win:keepall() as select * from " + SupportBean.class.getName() :
                "create table MyInfra (theString string, intPrimitive int)";
        epService.getEPAdministrator().createEPL(stmtTextCreate);

        SupportMessageAssertUtil.tryInvalid(epService, "on " + SupportBean_A.class.getName() + " select * from MyInfra where sum(intPrimitive) > 100",
                "Error validating expression: An aggregate function may not appear in a WHERE clause (use the HAVING clause) [on com.espertech.esper.support.bean.SupportBean_A select * from MyInfra where sum(intPrimitive) > 100]");

        SupportMessageAssertUtil.tryInvalid(epService, "on " + SupportBean_A.class.getName() + " insert into MyStream select * from DUMMY",
                "Named window or table 'DUMMY' has not been declared [on com.espertech.esper.support.bean.SupportBean_A insert into MyStream select * from DUMMY]");

        SupportMessageAssertUtil.tryInvalid(epService, "on " + SupportBean_A.class.getName() + " select prev(1, theString) from MyInfra",
                "Error starting statement: Failed to validate select-clause expression 'prev(1,theString)': Previous function cannot be used in this context [on com.espertech.esper.support.bean.SupportBean_A select prev(1, theString) from MyInfra]");

        epService.getEPAdministrator().destroyAllStatements();
        epService.getEPAdministrator().getConfiguration().removeEventType("MyInfra", false);
    }

    private void runAssertionPatternTimedSelect(boolean namedWindow)
    {
        // test for JIRA ESPER-332
        sendTimer(0, epService);

        String stmtTextCreate = namedWindow ?
                "create window MyInfra.win:keepall() as select * from " + SupportBean.class.getName() :
                "create table MyInfra as (theString string)";
        epService.getEPAdministrator().createEPL(stmtTextCreate);

        String stmtCount = "on pattern[every timer:interval(10 sec)] select count(eve), eve from MyInfra as eve";
        epService.getEPAdministrator().createEPL(stmtCount);

        String stmtTextOnSelect = "on pattern [ every timer:interval(10 sec)] select theString from MyInfra having count(theString) > 0";
        EPStatement stmt = epService.getEPAdministrator().createEPL(stmtTextOnSelect);
        stmt.addListener(listenerSelect);

        String stmtTextInsertOne = namedWindow ?
                "insert into MyInfra select * from " + SupportBean.class.getName() :
                "insert into MyInfra select theString from " + SupportBean.class.getName();
        epService.getEPAdministrator().createEPL(stmtTextInsertOne);

        sendTimer(11000, epService);
        assertFalse(listenerSelect.isInvoked());

        sendTimer(21000, epService);
        assertFalse(listenerSelect.isInvoked());

        sendSupportBean("E1", 1);
        sendTimer(31000, epService);
        assertEquals("E1", listenerSelect.assertOneGetNewAndReset().get("theString"));

        epService.getEPAdministrator().destroyAllStatements();
        epService.getEPAdministrator().getConfiguration().removeEventType("MyInfra", false);
    }

    private void runAssertionSelectAggregationHavingStreamWildcard(boolean namedWindow)
    {
        // create window
        String stmtTextCreate = namedWindow ?
                "create window MyInfra.win:keepall() as (a string, b int)" :
                "create table MyInfra as (a string primary key, b int primary key)";
        epService.getEPAdministrator().createEPL(stmtTextCreate);

        String stmtTextInsertOne = "insert into MyInfra select theString as a, intPrimitive as b from SupportBean";
        epService.getEPAdministrator().createEPL(stmtTextInsertOne);

        String stmtTextSelect = "on SupportBean_A select mwc.* as mwcwin from MyInfra mwc where id = a group by a having sum(b) = 20";
        EPStatementSPI select = (EPStatementSPI) epService.getEPAdministrator().createEPL(stmtTextSelect);
        assertFalse(select.getStatementContext().isStatelessSelect());
        select.addListener(listenerSelect);

        // send 3 event
        sendSupportBean("E1", 16);
        sendSupportBean("E2", 2);
        sendSupportBean("E1", 4);

        // fire trigger
        sendSupportBean_A("E1");
        EventBean[] events = listenerSelect.getLastNewData();
        assertEquals(2, events.length);
        assertEquals("E1", events[0].get("mwcwin.a"));
        assertEquals("E1", events[1].get("mwcwin.a"));

        epService.getEPAdministrator().destroyAllStatements();
        epService.getEPAdministrator().getConfiguration().removeEventType("MyInfra", false);
    }

    private void runAssertionWindowAgg(boolean namedWindow) {
        String eplCreate = namedWindow ?
                "create window MyInfra.win:keepall() as SupportBean" :
                "create table MyInfra(theString string primary key, intPrimitive int)";
        epService.getEPAdministrator().createEPL(eplCreate);
        String eplInsert = namedWindow ?
                "insert into MyInfra select * from SupportBean" :
                "insert into MyInfra select theString, intPrimitive from SupportBean";
        epService.getEPAdministrator().createEPL(eplInsert);
        epService.getEPAdministrator().createEPL("on S1 as s1 delete from MyInfra where s1.p10 = theString");

        EPStatement stmt = epService.getEPAdministrator().createEPL("on S0 as s0 " +
                "select window(win.*) as c0," +
                "window(win.*).where(v => v.intPrimitive < 2) as c1, " +
                "window(win.*).toMap(k=>k.theString,v=>v.intPrimitive) as c2 " +
                "from MyInfra as win");
        stmt.addListener(listenerSelect);

        SupportBean[] beans = new SupportBean[3];
        for (int i = 0; i < beans.length; i++) {
            beans[i] = new SupportBean("E" + i, i);
        }

        epService.getEPRuntime().sendEvent(beans[0]);
        epService.getEPRuntime().sendEvent(beans[1]);
        epService.getEPRuntime().sendEvent(new SupportBean_S0(10));
        assertReceived(namedWindow, beans, new int[]{0, 1}, new int[]{0, 1}, "E0,E1".split(","), new Object[] {0,1});

        // add bean
        epService.getEPRuntime().sendEvent(beans[2]);
        epService.getEPRuntime().sendEvent(new SupportBean_S0(10));
        assertReceived(namedWindow, beans, new int[]{0, 1, 2}, new int[]{0, 1}, "E0,E1,E2".split(","), new Object[] {0,1, 2});

        // delete bean
        epService.getEPRuntime().sendEvent(new SupportBean_S1(11, "E1"));
        epService.getEPRuntime().sendEvent(new SupportBean_S0(12));
        assertReceived(namedWindow, beans, new int[]{0, 2}, new int[]{0}, "E0,E2".split(","), new Object[] {0,2});

        // delete another bean
        epService.getEPRuntime().sendEvent(new SupportBean_S1(13, "E0"));
        epService.getEPRuntime().sendEvent(new SupportBean_S0(14));
        assertReceived(namedWindow, beans, new int[]{2}, new int[0], "E2".split(","), new Object[] {2});

        // delete last bean
        epService.getEPRuntime().sendEvent(new SupportBean_S1(15, "E2"));
        epService.getEPRuntime().sendEvent(new SupportBean_S0(16));
        assertReceived(namedWindow, beans, null, null, null, null);

        epService.getEPAdministrator().destroyAllStatements();
        epService.getEPAdministrator().getConfiguration().removeEventType("MyInfra", false);
    }

    private void assertReceived(boolean namedWindow, SupportBean[] beans, int[] indexesAll, int[] indexesWhere, String[] mapKeys, Object[] mapValues) {
        EventBean received = listenerSelect.assertOneGetNewAndReset();
        Object[] expectedAll;
        Object[] expectedWhere;
        if (!namedWindow) {
            expectedAll = SupportBean.getOAStringAndIntPerIndex(beans, indexesAll);
            expectedWhere = SupportBean.getOAStringAndIntPerIndex(beans, indexesWhere);
            EPAssertionUtil.assertEqualsAnyOrder(expectedAll, (Object[]) received.get("c0"));
            Collection receivedColl = (Collection) received.get("c1");
            EPAssertionUtil.assertEqualsAnyOrder(expectedWhere, receivedColl == null ? null : receivedColl.toArray());
        }
        else {
            expectedAll = SupportBean.getBeansPerIndex(beans, indexesAll);
            expectedWhere = SupportBean.getBeansPerIndex(beans, indexesWhere);
            EPAssertionUtil.assertEqualsExactOrder(expectedAll, (Object[]) received.get("c0"));
            EPAssertionUtil.assertEqualsExactOrder(expectedWhere, (Collection) received.get("c1"));
        }
        EPAssertionUtil.assertPropsMap((Map) received.get("c2"), mapKeys, mapValues);
    }

    public void runAssertionOnSelectIndexChoice(boolean isNamedWindow) {
        epService.getEPAdministrator().getConfiguration().addEventType("SSB1", SupportSimpleBeanOne.class);
        epService.getEPAdministrator().getConfiguration().addEventType("SSB2", SupportSimpleBeanTwo.class);

        String backingUniqueS1 = "unique hash={s1(string)} btree={}";
        String backingUniqueS1L1 = "unique hash={s1(string),l1(long)} btree={}";
        String backingNonUniqueS1 = "non-unique hash={s1(string)} btree={}";
        String backingUniqueS1D1 = "unique hash={s1(string),d1(double)} btree={}";
        String backingBtreeI1 = "non-unique hash={} btree={i1(int)}";
        String backingBtreeD1 = "non-unique hash={} btree={d1(double)}";
        String expectedIdxNameS1 = isNamedWindow ? null : "MyInfra";

        Object[] preloadedEventsOne = new Object[] {new SupportSimpleBeanOne("E1", 10, 11, 12), new SupportSimpleBeanOne("E2", 20, 21, 22)};
        IndexAssertionEventSend eventSendAssertion = new IndexAssertionEventSend() {
            public void run() {
                String[] fields = "ssb2.s2,ssb1.s1,ssb1.i1".split(",");
                epService.getEPRuntime().sendEvent(new SupportSimpleBeanTwo("E2", 50, 21, 22));
                EPAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fields, new Object[]{"E2", "E2", 20});
                epService.getEPRuntime().sendEvent(new SupportSimpleBeanTwo("E1", 60, 11, 12));
                EPAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fields, new Object[] {"E1", "E1", 10});
            }
        };

        // single index one field (std:unique(s1))
        assertIndexChoice(isNamedWindow, new String[0], preloadedEventsOne, "std:unique(s1)",
            new IndexAssertion[] {
                    new IndexAssertion(null, "s1 = s2", expectedIdxNameS1, backingUniqueS1, eventSendAssertion),
                    new IndexAssertion(null, "s1 = ssb2.s2 and l1 = ssb2.l2", expectedIdxNameS1, backingUniqueS1, eventSendAssertion),
                    new IndexAssertion("@Hint('index(One)')", "s1 = ssb2.s2 and l1 = ssb2.l2", expectedIdxNameS1, backingUniqueS1, eventSendAssertion),
                    new IndexAssertion("@Hint('index(Two,bust)')", "s1 = ssb2.s2 and l1 = ssb2.l2"),// busted
            });

        // single index one field (std:unique(s1))
        if (isNamedWindow) {
            String[] indexOneField = new String[] {"create unique index One on MyInfra (s1)"};
            assertIndexChoice(isNamedWindow, indexOneField, preloadedEventsOne, "std:unique(s1)",
                    new IndexAssertion[] {
                            new IndexAssertion(null, "s1 = s2", "One", backingUniqueS1, eventSendAssertion),
                            new IndexAssertion(null, "s1 = ssb2.s2 and l1 = ssb2.l2", "One", backingUniqueS1, eventSendAssertion),
                            new IndexAssertion("@Hint('index(One)')", "s1 = ssb2.s2 and l1 = ssb2.l2", "One", backingUniqueS1, eventSendAssertion),
                            new IndexAssertion("@Hint('index(Two,bust)')", "s1 = ssb2.s2 and l1 = ssb2.l2"),// busted
                    });
        }

        // single index two field  (std:unique(s1))
        String[] indexTwoField = new String[] {"create unique index One on MyInfra (s1, l1)"};
        assertIndexChoice(isNamedWindow, indexTwoField, preloadedEventsOne, "std:unique(s1)",
                new IndexAssertion[] {
                        new IndexAssertion(null, "s1 = ssb2.s2", expectedIdxNameS1, backingUniqueS1, eventSendAssertion),
                        new IndexAssertion(null, "s1 = ssb2.s2 and l1 = ssb2.l2", "One", backingUniqueS1L1, eventSendAssertion),
                });
        assertIndexChoice(isNamedWindow, indexTwoField, preloadedEventsOne, "win:keepall()",
                new IndexAssertion[] {
                        new IndexAssertion(null, "s1 = ssb2.s2", expectedIdxNameS1, isNamedWindow ? backingNonUniqueS1 : backingUniqueS1, eventSendAssertion),
                        new IndexAssertion(null, "s1 = ssb2.s2 and l1 = ssb2.l2", "One", backingUniqueS1L1, eventSendAssertion),
                });

        // two index one unique  (std:unique(s1))
        String[] indexSetTwo = new String[] {
                "create index One on MyInfra (s1)",
                "create unique index Two on MyInfra (s1, d1)"};
        assertIndexChoice(isNamedWindow, indexSetTwo, preloadedEventsOne, "std:unique(s1)",
                new IndexAssertion[] {
                        new IndexAssertion(null, "s1 = ssb2.s2", isNamedWindow ? "One" : "MyInfra", isNamedWindow ? backingNonUniqueS1 : backingUniqueS1, eventSendAssertion),
                        new IndexAssertion(null, "s1 = ssb2.s2 and l1 = ssb2.l2", isNamedWindow ? "One" : "MyInfra", isNamedWindow ? backingNonUniqueS1 : backingUniqueS1, eventSendAssertion),
                        new IndexAssertion("@Hint('index(One)')", "s1 = ssb2.s2 and l1 = ssb2.l2", "One", backingNonUniqueS1, eventSendAssertion),
                        new IndexAssertion("@Hint('index(Two,One)')", "s1 = ssb2.s2 and l1 = ssb2.l2", "One", backingNonUniqueS1, eventSendAssertion),
                        new IndexAssertion("@Hint('index(Two,bust)')", "s1 = ssb2.s2 and l1 = ssb2.l2"), // busted
                        new IndexAssertion("@Hint('index(explicit,bust)')", "s1 = ssb2.s2 and l1 = ssb2.l2", isNamedWindow ? "One" : "MyInfra", isNamedWindow ? backingNonUniqueS1 : backingUniqueS1, eventSendAssertion),
                        new IndexAssertion(null, "s1 = ssb2.s2 and d1 = ssb2.d2 and l1 = ssb2.l2", isNamedWindow ? "Two" : "MyInfra", isNamedWindow ? backingUniqueS1D1 : backingUniqueS1, eventSendAssertion),
                        new IndexAssertion("@Hint('index(explicit,bust)')", "d1 = ssb2.d2 and l1 = ssb2.l2"), // busted
                });

        // two index one unique  (win:keepall)
        assertIndexChoice(isNamedWindow, indexSetTwo, preloadedEventsOne, "win:keepall()",
                new IndexAssertion[] {
                        new IndexAssertion(null, "s1 = ssb2.s2", isNamedWindow ? "One" : "MyInfra", isNamedWindow ? backingNonUniqueS1 : backingUniqueS1, eventSendAssertion),
                        new IndexAssertion(null, "s1 = ssb2.s2 and l1 = ssb2.l2", isNamedWindow ? "One" : "MyInfra", isNamedWindow ? backingNonUniqueS1 : backingUniqueS1, eventSendAssertion),
                        new IndexAssertion("@Hint('index(One)')", "s1 = ssb2.s2 and l1 = ssb2.l2", "One", backingNonUniqueS1, eventSendAssertion),
                        new IndexAssertion("@Hint('index(Two,One)')", "s1 = ssb2.s2 and l1 = ssb2.l2", "One", backingNonUniqueS1, eventSendAssertion),
                        new IndexAssertion("@Hint('index(Two,bust)')", "s1 = ssb2.s2 and l1 = ssb2.l2"), // busted
                        new IndexAssertion("@Hint('index(explicit,bust)')", "s1 = ssb2.s2 and l1 = ssb2.l2", isNamedWindow ? "One" : "MyInfra", isNamedWindow ? backingNonUniqueS1 : backingUniqueS1, eventSendAssertion),
                        new IndexAssertion(null, "s1 = ssb2.s2 and d1 = ssb2.d2 and l1 = ssb2.l2", isNamedWindow ? "Two" : "MyInfra", isNamedWindow ? backingUniqueS1D1 : backingUniqueS1, eventSendAssertion),
                        new IndexAssertion("@Hint('index(explicit,bust)')", "d1 = ssb2.d2 and l1 = ssb2.l2"), // busted
                });

        // range  (std:unique(s1))
        IndexAssertionEventSend noAssertion = new IndexAssertionEventSend() {
            public void run() {
            }
        };
        String[] indexSetThree = new String[] {
                "create index One on MyInfra (i1 btree)",
                "create index Two on MyInfra (d1 btree)"};
        assertIndexChoice(isNamedWindow, indexSetThree, preloadedEventsOne, "std:unique(s1)",
                new IndexAssertion[] {
                        new IndexAssertion(null, "i1 between 1 and 10", "One", backingBtreeI1, noAssertion),
                        new IndexAssertion(null, "d1 between 1 and 10", "Two", backingBtreeD1, noAssertion),
                        new IndexAssertion("@Hint('index(One, bust)')", "d1 between 1 and 10"),// busted
                });

        // rel ops
        Object[] preloadedEventsRelOp = new Object[] {new SupportSimpleBeanOne("E1", 10, 11, 12)};
        IndexAssertionEventSend relOpAssertion = new IndexAssertionEventSend() {
            public void run() {
                String[] fields = "ssb2.s2,ssb1.s1,ssb1.i1".split(",");
                epService.getEPRuntime().sendEvent(new SupportSimpleBeanTwo("EX", 0, 0, 0));
                EPAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fields, new Object[]{"EX", "E1", 10});
            }
        };
        assertIndexChoice(isNamedWindow, new String[0], preloadedEventsRelOp, "win:keepall()",
                new IndexAssertion[] {
                        new IndexAssertion(null, "9 < i1", null, isNamedWindow ? backingBtreeI1 : null, relOpAssertion),
                        new IndexAssertion(null, "10 <= i1", null, isNamedWindow ? backingBtreeI1 : null, relOpAssertion),
                        new IndexAssertion(null, "i1 <= 10", null, isNamedWindow ? backingBtreeI1 : null, relOpAssertion),
                        new IndexAssertion(null, "i1 < 11", null, isNamedWindow ? backingBtreeI1 : null, relOpAssertion),
                        new IndexAssertion(null, "11 > i1", null, isNamedWindow ? backingBtreeI1 : null, relOpAssertion),
                });
    }

    private void assertIndexChoice(boolean isNamedWindow, String[] indexes, Object[] preloadedEvents, String datawindow,
                                   IndexAssertion[] assertions) {

        String eplCreate = isNamedWindow ?
                "@name('create-window') create window MyInfra." + datawindow + " as SSB1" :
                "@name('create-table') create table MyInfra(s1 string primary key, i1 int, d1 double, l1 long)";
        epService.getEPAdministrator().createEPL(eplCreate);

        epService.getEPAdministrator().createEPL("insert into MyInfra select s1,i1,d1,l1 from SSB1");
        for (String index : indexes) {
            epService.getEPAdministrator().createEPL(index, "create-index '" + index + "'");
        }
        for (Object event : preloadedEvents) {
            epService.getEPRuntime().sendEvent(event);
        }

        int count = 0;
        for (IndexAssertion assertion : assertions) {
            log.info("======= Testing #" + count++);
            String consumeEpl = INDEX_CALLBACK_HOOK +
                    (assertion.getHint() == null ? "" : assertion.getHint()) +
                    "@name('on-select') on SSB2 as ssb2 " +
                    "select * " +
                    "from MyInfra as ssb1 where " + assertion.getWhereClause();

            EPStatement consumeStmt;
            try {
                consumeStmt = epService.getEPAdministrator().createEPL(consumeEpl);
            }
            catch (EPStatementException ex) {
                if (assertion.getEventSendAssertion() == null) {
                    // no assertion, expected
                    assertTrue(ex.getMessage().contains("index hint busted"));
                    continue;
                }
                throw new RuntimeException("Unexpected statement exception: " + ex.getMessage(), ex);
            }

            // assert index and access
            SupportQueryPlanIndexHook.assertOnExprTableAndReset(assertion.getExpectedIndexName(), assertion.getIndexBackingClass());
            consumeStmt.addListener(listenerSelect);
            assertion.getEventSendAssertion().run();
            consumeStmt.destroy();
        }

        epService.getEPAdministrator().destroyAllStatements();
        epService.getEPAdministrator().getConfiguration().removeEventType("MyInfra", false);
    }

    private SupportBean_A sendSupportBean_A(String id)
    {
        SupportBean_A bean = new SupportBean_A(id);
        epService.getEPRuntime().sendEvent(bean);
        return bean;
    }

    private SupportBean_B sendSupportBean_B(String id)
    {
        SupportBean_B bean = new SupportBean_B(id);
        epService.getEPRuntime().sendEvent(bean);
        return bean;
    }

    private SupportBean sendSupportBean(String theString, int intPrimitive)
    {
        SupportBean bean = new SupportBean();
        bean.setTheString(theString);
        bean.setIntPrimitive(intPrimitive);
        epService.getEPRuntime().sendEvent(bean);
        return bean;
    }

    private void sendTimer(long timeInMSec, EPServiceProvider epService)
    {
        CurrentTimeEvent theEvent = new CurrentTimeEvent(timeInMSec);
        EPRuntime runtime = epService.getEPRuntime();
        runtime.sendEvent(theEvent);
    }
}
