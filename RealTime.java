/*
 * ****************************************************************************
 * Copyright VMware, Inc. 2010-2016.  All Rights Reserved.
 * ****************************************************************************
 *
 * This software is made available for use under the terms of the BSD
 * 3-Clause license:
 *
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright 
 *    notice, this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in 
 *    the documentation and/or other materials provided with the 
 *    distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its 
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE 
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package com.vmware.performance;

import com.vmware.common.annotations.Action;
import com.vmware.common.annotations.Option;
import com.vmware.common.annotations.Sample;
import com.vmware.connection.ConnectedVimServiceBase;
import com.vmware.vim25.*;

import javax.xml.ws.soap.SOAPFaultException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

/**
 * <pre>
 * RealTime
 *
 * This sample displays performance measurements from the current time
 * at the console
 *
 * <b>Parameters:</b>
 * url        [required] : url of the web service
 * username   [required] : username for the authentication
 * password   [required] : password for the authentication
 * vmname     [required] : name of the vm
 *
 * <b>Command Line:</b>
 * run.bat com.vmware.performance.RealTime
 * --url [webservice url]  --username [user] --password [password]
 * --vmname [name of the vm]
 * </pre>
 */
@Sample(name = "realtime-performance",
        description = " This sample displays " +
                "performance measurements from the current time " +
                " at the console"
)
public class RealTime extends ConnectedVimServiceBase {
    private ManagedObjectReference propCollectorRef;
    private ManagedObjectReference perfManager;

    private String virtualmachinename;

    @Option(name = "vmname", description = "name of the vm")
    public void setVirtualmachinename(String virtualmachinename) {
        this.virtualmachinename = virtualmachinename;
    }

    /**
     * Uses the new RetrievePropertiesEx method to emulate the now deprecated
     * RetrieveProperties method.
     *
     * @param listpfs
     * @return list of object content
     * @throws Exception
     */
    List<ObjectContent> retrievePropertiesAllObjects(
            List<PropertyFilterSpec> listpfs) {

        RetrieveOptions propObjectRetrieveOpts = new RetrieveOptions();

        List<ObjectContent> listobjcontent = new ArrayList<ObjectContent>();

        try {
            RetrieveResult rslts =
                    vimPort.retrievePropertiesEx(propCollectorRef, listpfs,
                            propObjectRetrieveOpts);
            if (rslts != null && rslts.getObjects() != null
                    && !rslts.getObjects().isEmpty()) {
                listobjcontent.addAll(rslts.getObjects());
            }
            String token = null;
            if (rslts != null && rslts.getToken() != null) {
                token = rslts.getToken();
            }
            while (token != null && !token.isEmpty()) {
                rslts =
                        vimPort.continueRetrievePropertiesEx(propCollectorRef, token);
                token = null;
                if (rslts != null) {
                    token = rslts.getToken();
                    if (rslts.getObjects() != null && !rslts.getObjects().isEmpty()) {
                        listobjcontent.addAll(rslts.getObjects());
                    }
                }
            }
        } catch (SOAPFaultException sfe) {
            printSoapFaultException(sfe);
        } catch (Exception e) {
            System.out.println(" : Failed Getting Contents");
            e.printStackTrace();
        }

        return listobjcontent;
    }

    void displayValues(List<PerfEntityMetricBase> values,
                       Map<Integer, PerfCounterInfo> counters) {

        for (int i = 0; i < values.size(); ++i) {
            List<PerfMetricSeries> listpems =
                    ((PerfEntityMetric) values.get(i)).getValue();
            List<PerfSampleInfo> listinfo =
                    ((PerfEntityMetric) values.get(i)).getSampleInfo();

            System.out.println("Sample time range: "
                    + listinfo.get(0).getTimestamp().toString() + " - "
                    + listinfo.get(listinfo.size() - 1).getTimestamp().toString());
            for (int vi = 0; vi < listpems.size(); ++vi) {
                PerfCounterInfo pci =
                        counters.get(new Integer(listpems.get(vi).getId()
                                .getCounterId()));
                if (pci != null) {
                    System.out.println(pci.getNameInfo().getSummary());
                }
                if (listpems.get(vi) instanceof PerfMetricIntSeries) {
                    PerfMetricIntSeries val = (PerfMetricIntSeries) listpems.get(vi);
                    List<Long> lislon = val.getValue();
                    for (Long k : lislon) {
                        System.out.print(k + " ");
                    }
                    System.out.println();
                }
            }
        }
    }

    /**
     * This method initializes all the performance counters available on the
     * system it is connected to. The performance counters are stored in the
     * hashmap counters with group.counter.rolluptype being the key and id being
     * the value.
     */
    List<PerfCounterInfo> getPerfCounters() {
        List<PerfCounterInfo> pciArr = null;

        try {
            // Create Property Spec
            PropertySpec propertySpec = new PropertySpec();
            propertySpec.setAll(Boolean.FALSE);
            propertySpec.getPathSet().add("perfCounter");
            propertySpec.setType("PerformanceManager");
            List<PropertySpec> propertySpecs = new ArrayList<PropertySpec>();
            propertySpecs.add(propertySpec);

            // Now create Object Spec
            ObjectSpec objectSpec = new ObjectSpec();
            objectSpec.setObj(perfManager);
            List<ObjectSpec> objectSpecs = new ArrayList<ObjectSpec>();
            objectSpecs.add(objectSpec);

            // Create PropertyFilterSpec using the PropertySpec and ObjectPec
            // created above.
            PropertyFilterSpec propertyFilterSpec = new PropertyFilterSpec();
            propertyFilterSpec.getPropSet().add(propertySpec);
            propertyFilterSpec.getObjectSet().add(objectSpec);

            List<PropertyFilterSpec> propertyFilterSpecs =
                    new ArrayList<PropertyFilterSpec>();
            propertyFilterSpecs.add(propertyFilterSpec);

            List<PropertyFilterSpec> listpfs =
                    new ArrayList<PropertyFilterSpec>(1);
            listpfs.add(propertyFilterSpec);
            List<ObjectContent> listobjcont =
                    retrievePropertiesAllObjects(listpfs);

            if (listobjcont != null) {
                for (ObjectContent oc : listobjcont) {
                    List<DynamicProperty> dps = oc.getPropSet();
                    if (dps != null) {
                        for (DynamicProperty dp : dps) {
                            List<PerfCounterInfo> pcinfolist =
                                    ((ArrayOfPerfCounterInfo) dp.getVal())
                                            .getPerfCounterInfo();
                            pciArr = pcinfolist;
                        }
                    }
                }
            }
        } catch (SOAPFaultException sfe) {
            printSoapFaultException(sfe);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return pciArr;
    }


    void doRealTime() throws IOException, RuntimeFaultFaultMsg, InterruptedException, InvalidPropertyFaultMsg {
        Map<String, ManagedObjectReference> vms = getMOREFs.inContainerByType(serviceContent
                .getRootFolder(), "VirtualMachine");
        ManagedObjectReference vmmor = vms.get(virtualmachinename);

        if (vmmor != null) {
            List<PerfCounterInfo> cInfo = getPerfCounters();
            List<PerfCounterInfo> vmCpuCounters = new ArrayList<PerfCounterInfo>();
            for (int i = 0; i < cInfo.size(); ++i) {
                if ("cpu".equalsIgnoreCase(cInfo.get(i).getGroupInfo().getKey())) {
                    vmCpuCounters.add(cInfo.get(i));
                }
            }
            Map<Integer, PerfCounterInfo> counters =
                    new HashMap<Integer, PerfCounterInfo>();
            while (true) {
                int i = 0;
                for (Iterator<PerfCounterInfo> it = vmCpuCounters.iterator(); it
                        .hasNext(); ) {
                    PerfCounterInfo pcInfo = it.next();
                    System.out.println(++i + " - "
                            + pcInfo.getNameInfo().getSummary());
                }
                System.out.println("Please select a counter from"
                        + " the above list" + "\nEnter 0 to end: ");
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(System.in));
                i = Integer.parseInt(reader.readLine());
                if (i > vmCpuCounters.size()) {
                    System.out.println("*** Value out of range!");
                } else {
                    --i;
                    if (i < 0) {
                        return;
                    }
                    PerfCounterInfo pcInfo = vmCpuCounters.get(i);
                    counters.put(new Integer(pcInfo.getKey()), pcInfo);
                    break;
                }
            }
            List<PerfMetricId> listpermeid =
                    vimPort.queryAvailablePerfMetric(perfManager, vmmor, null, null,
                            new Integer(20));
            List<PerfMetricId> mMetrics = new ArrayList<PerfMetricId>();
            if (listpermeid != null) {
                for (int index = 0; index < listpermeid.size(); ++index) {
                    if (counters.containsKey(new Integer(listpermeid.get(index)
                            .getCounterId()))) {
                        mMetrics.add(listpermeid.get(index));
                    }
                }
            }
            monitorPerformance(perfManager, vmmor, mMetrics, counters);
        } else {
            System.out.println("Virtual Machine " + virtualmachinename
                    + " not found");
        }
    }

    /**
     * @param pmRef
     * @param vmRef
     * @param mMetrics
     * @param counters
     * @throws Exception
     */
    void monitorPerformance(ManagedObjectReference pmRef,
                            ManagedObjectReference vmRef, List<PerfMetricId> mMetrics,
                            Map<Integer, PerfCounterInfo> counters) throws RuntimeFaultFaultMsg, InterruptedException {
        PerfQuerySpec qSpec = new PerfQuerySpec();
        qSpec.setEntity(vmRef);
        qSpec.setMaxSample(new Integer(10));
        qSpec.getMetricId().addAll(mMetrics);
        qSpec.setIntervalId(new Integer(20));

        List<PerfQuerySpec> qSpecs = new ArrayList<PerfQuerySpec>();
        qSpecs.add(qSpec);
        while (true) {
            List<PerfEntityMetricBase> listpemb = vimPort.queryPerf(pmRef, qSpecs);
            List<PerfEntityMetricBase> pValues = listpemb;
            if (pValues != null) {
                displayValues(pValues, counters);
            }
            System.out.println("Sleeping 10 seconds...");
            Thread.sleep(10 * 1000);
        }
    }

    void printSoapFaultException(SOAPFaultException sfe) {
        System.out.println("SOAP Fault -");
        if (sfe.getFault().hasDetail()) {
            System.out.println(sfe.getFault().getDetail().getFirstChild()
                    .getLocalName());
        }
        if (sfe.getFault().getFaultString() != null) {
            System.out.println("\n Message: " + sfe.getFault().getFaultString());
        }
    }

    @Action
    public void run() throws RuntimeFaultFaultMsg, IOException, InterruptedException, InvalidPropertyFaultMsg {
        propCollectorRef = serviceContent.getPropertyCollector();
        perfManager = serviceContent.getPerfManager();
        doRealTime();
    }
}
