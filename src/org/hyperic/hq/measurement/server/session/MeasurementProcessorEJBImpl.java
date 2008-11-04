/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 * 
 * Copyright (C) [2004, 2005, 2006], Hyperic, Inc.
 * This file is part of HQ.
 * 
 * HQ is free software; you can redistribute it and/or modify
 * it under the terms version 2 of the GNU General Public License as
 * published by the Free Software Foundation. This program is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA.
 */

package org.hyperic.hq.measurement.server.session;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.ejb.SessionBean;
import javax.ejb.SessionContext;

import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.Agent;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.measurement.MeasurementScheduleException;
import org.hyperic.hq.measurement.MeasurementUnscheduleException;
import org.hyperic.hq.measurement.agent.client.AgentMonitor;
import org.hyperic.hq.measurement.server.session.Measurement;
import org.hyperic.hq.measurement.monitor.MonitorAgentException;
import org.hyperic.hq.measurement.shared.MeasurementProcessorLocal;
import org.hyperic.hq.measurement.shared.MeasurementProcessorUtil;
import org.hyperic.hq.measurement.shared.SRNManagerLocal;
import org.hyperic.hq.zevents.ZeventManager;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.ObjectNotFoundException;

/**
 * @ejb:bean name="MeasurementProcessor"
 *      jndi-name="ejb/measurement/MeasurementProcessor"
 *      local-jndi-name="LocalMeasurementProcessor"
 *      view-type="local"
 *      type="Stateless"
 * @ejb:transaction type="Required"
 */
public class MeasurementProcessorEJBImpl 
    extends SessionEJB 
    implements SessionBean 
{
    private static final String logCtx = 
        MeasurementProcessorEJBImpl.class.getName();
    private final Log log = LogFactory.getLog(logCtx);

    /**
     * Ping the agent to make sure it's up
     * @ejb:interface-method
     */
    public boolean ping(Agent a)
        throws PermissionException {

        AgentMonitor monitor = new AgentMonitor();
        return monitor.ping(a);
    }

    /**
     * Schedule a DerivedMeasurement and all of its dependent
     * measurements to be collected and calculated.
     *
     * @ejb:interface-method
     *
     * @param entId The AppdefEntityID to schedule
     * @param measurements The list of measurements to schedule
     */
    public void schedule(AppdefEntityID entId, List measurements)
        throws PermissionException, MeasurementScheduleException,
               MonitorAgentException
    {
        SRNManagerLocal srnManager = getSRNManager();
        long minInterval = Long.MAX_VALUE;

        ArrayList events = new ArrayList();
        for (Iterator i = measurements.iterator(); i.hasNext(); ) {
            Measurement dm = (Measurement)i.next();

            minInterval = Math.min(minInterval, dm.getInterval());

            MeasurementScheduleZevent event =
                    new MeasurementScheduleZevent(dm.getId().intValue(),
                                                  dm.getInterval());
            events.add(event);
        }

        // Schedule the measurements
        int srnNumber = srnManager.incrementSrn(entId, minInterval);
        scheduleMeasurements(entId, measurements, srnNumber);

        ZeventManager.getInstance().enqueueEventsAfterCommit(events);
    }

    private void unschedule(Agent a, AppdefEntityID[] entIds)
        throws MeasurementUnscheduleException, MonitorAgentException {
        SRNManagerLocal srnManager = getSRNManager();
        for (int i = 0; i < entIds.length; i++) {
            try {
                srnManager.removeSrn(entIds[i]);
            } catch (ObjectNotFoundException e) {
                // Ok to ignore, this is the first time scheduling metrics
                // for this resource.
            }
        }

        AgentMonitor monitor = new AgentMonitor();
        monitor.unschedule(a, entIds);
    }
    
    /** Unschedule metrics of multiple appdef entities
     * @ejb:interface-method
     * @param agentToken the entity whose agent will be contacted for the
     * unschedule
     * @param entIds the entity IDs whose metrics should be unscheduled
     * @throws MeasurementUnscheduleException if an error occurs
     */
    public void unschedule(String agentToken, AppdefEntityID[] entIds)
        throws MeasurementUnscheduleException {
        try {
            // Get the agent from agent token
            Agent a = getAgent(agentToken);
            unschedule(a, entIds);
        } catch (MonitorAgentException e) {
            log.warn("Error unscheduling metrics: " + e.getMessage());
        }
    }
    
    /** Unschedule metrics of multiple appdef entities
     * @ejb:interface-method
     * @param agentEnt the entity whose agent will be contacted for the unschedule
     * @param entIds the entity IDs whose metrics should be unscheduled
     * @throws MeasurementUnscheduleException if an error occurs
     */
    public void unschedule(AppdefEntityID agentEnt, AppdefEntityID[] entIds)
        throws MeasurementUnscheduleException {
        try {
            // Get the agent IP and Port from server ID
            Agent a = getAgent(agentEnt);
            unschedule(a, entIds);
        } catch (MonitorAgentException e) {
            log.warn("Error unscheduling metrics: " + e.getMessage());
        }
    }

    /** Unschedule a measurement
     * @ejb:interface-method
     * @param entId The Appdef ID to unschedule
     * @throws MeasurementUnscheduleException if an error occurs
     */
    public void unschedule(AppdefEntityID entId)
        throws MeasurementUnscheduleException, PermissionException {
        this.unschedule(entId, new AppdefEntityID[] { entId });
    }

    private void scheduleMeasurements(AppdefEntityID entId,
                                      List measurements, int srnVersion)
        throws PermissionException, MonitorAgentException {

        Agent a = getAgent(entId);
        SRN srn = new SRN(entId, srnVersion);

        AgentMonitor monitor = new AgentMonitor();

        Measurement[] meas = (Measurement[])
            measurements.toArray(new Measurement[measurements.size()]);

        // Then schedule
        monitor.schedule(a, srn, meas);
    }

    public static MeasurementProcessorLocal getOne() {
        try {
            return MeasurementProcessorUtil.getLocalHome().create();
        } catch(Exception e) {
            throw new SystemException(e);
        }
    }
    
    /**
     * @ejb:create-method
     */
    public void ejbCreate() {}
    public void ejbPostCreate() {}
    public void ejbActivate() {}
    public void ejbPassivate() {}
    public void ejbRemove() {}
    public void setSessionContext(SessionContext ctx) {}
}
