/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.wireless.tests;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.util.Arrays;

/**
 * Helper class to get device radio settings
 */
public class RadioHelper {
    private final static String[] PING_SERVER_LIST = {"www.google.com", "www.facebook.com",
        "www.bing.com", "www.ask.com", "www.yahoo.com"};
    private final int RETRY_ATTEMPTS = 3;
    private final int ACTIVATION_WAITING_TIME =  5 * 60 * 1000; // 5 minutes;
    private ITestDevice mDevice;

    RadioHelper(ITestDevice device) {
        mDevice = device;
    }

    /**
     * Gets the {@link IRunUtil} instance to use.
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * Get phone type 0 - None, 1 - GSM, 2 - CDMA
     */
    private String getPhoneType() throws DeviceNotAvailableException {
        return mDevice.getPropertySync("gsm.current.phone-type");
    }

    /**
     * Get sim state
     */
    private String getSimState() throws DeviceNotAvailableException {
        return mDevice.getPropertySync("gsm.sim.state");
    }

    /**
     * Verify whether a device is a CDMA only device
     * @return true for CDMA only device, false for GSM or LTE device
     * @throws DeviceNotAvailableException
     */
    public boolean isCdmaDevice() throws DeviceNotAvailableException {
        // Wait 30 seconds for SIM to load
        getRunUtil().sleep(30*1000);
        String phoneType = null;
        String simState = null;
        for (int i = 0; i < RETRY_ATTEMPTS && (phoneType == null || simState == null); i++) {
            phoneType = getPhoneType();
            simState = getSimState();
            CLog.d("phonetype: %s", phoneType);
            CLog.d("gsm.sim.state: %s", simState);
            RunUtil.getDefault().sleep(5 * 1000);
        }

        if (phoneType == null || simState == null) {
            CLog.d("Error: phoneType or simState is null.");
            return false;
        }

        if ((phoneType.compareToIgnoreCase("2") == 0) &&
            (simState.compareToIgnoreCase("UNKNOWN") == 0)) {
            // GSM device as phoneType "1"
            // LTE device should have SIM state set to "READY"
            CLog.d("it is a CDMA device, return true");
            return true;
        }
        return false;
    }

    public void resetBootComplete() throws DeviceNotAvailableException {
        mDevice.executeShellCommand("setprop dev.bootcomplete 0");
    }

    public boolean waitForBootComplete()
        throws DeviceNotAvailableException {
        long deviceBootTime = 5 * 60 * 1000;
        long startTime = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startTime) < deviceBootTime) {
            String output = mDevice.executeShellCommand("getprop dev.bootcomplete");
            output = output.replace('#', ' ').trim();
            if (output.equals("1")) {
                return true;
            }
            getRunUtil().sleep(5*1000);
        }
        return false;
    }

    public boolean pingTest() throws DeviceNotAvailableException {
        String failString = "ping: unknown host";
        // assume the chance that all servers are down is very small
        for (int i = 0; i < PING_SERVER_LIST.length; i++ ) {
            String host = PING_SERVER_LIST[i];
            CLog.d("Start ping test, ping %s", host);
            String res = mDevice.executeShellCommand("ping -c 10 -w 100 " + host);
            CLog.d("res: %s", res);
            if (!res.contains(failString)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Activate a device if it is needed.
     * @return true if the activation is successful.
     * @throws DeviceNotAvailableException
     */
    public boolean radioActivation() throws DeviceNotAvailableException {
        if (!isCdmaDevice()) {
            // for GSM device and LTE device
            CLog.d("not a CDMA device, no need to activiate the device");
            return true;
        } else if (pingTest()) {
            // for CDMA device which has been activiated (e.g. no radio updates)
            CLog.d("CDMA device has been activated.");
            return true;
        }

        // Activate a CDMA device which doesn't have data connection yet
        for (int i = 0; i < RETRY_ATTEMPTS; i++ ) {
            mDevice.executeShellCommand("radiooptions 8 *22899");
            long startTime = System.currentTimeMillis();
            while ((System.currentTimeMillis() - startTime) < ACTIVATION_WAITING_TIME) {
                getRunUtil().sleep(30 * 1000);
                if (pingTest()) {
                    return true;
                }
            }
        }
        return false;
    }
}