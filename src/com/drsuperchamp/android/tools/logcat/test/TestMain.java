/*
 * Copyright (C) 2011 Dr.SuperChamp
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

package com.drsuperchamp.android.tools.logcat.test;

import com.drsuperchamp.android.tools.logcat.core.AdbWrapper;
import com.drsuperchamp.android.tools.logcat.core.FilterOutput;
import com.drsuperchamp.android.tools.logcat.core.LogCatWrapper;
import com.drsuperchamp.android.tools.logcat.core.AdbWrapper.DeviceConnectionListener;
import com.drsuperchamp.android.tools.logcat.core.LogCatWrapper.LogMessage;

public class TestMain implements DeviceConnectionListener {
    private static final String ADB_PATH = "/home/wpark/android/sdk/android-sdk-linux_x86/platform-tools/adb";
    AdbWrapper mAdb;

    public TestMain() {
        mAdb = AdbWrapper.getInstance();
        mAdb.connect(ADB_PATH, this);
    }

    @Override
    public void deviceConnected(String devSerialNumber) {
        Worker worker = new Worker(devSerialNumber);
        worker.start();
    }

    @Override
    public void deviceDisconnected(String devSerialNumber) {
    }

    private final class Worker extends Thread {
        String mDevSerialNumber;
        LogCatWrapper mLogcat;
        FilterOutput mDefaultFilterOutput;

        public Worker(String devSerialNumber) {
            mDevSerialNumber = devSerialNumber;
            mLogcat = new LogCatWrapper(devSerialNumber, 10000);
            mDefaultFilterOutput = new DefaultFilterOutput();
            mLogcat.setDefaultFilterOutput(mDefaultFilterOutput);
        }
        @Override
        public void run() {
            mAdb.executeShellCommand(mDevSerialNumber, "logcat -v long", mLogcat.getShellOutputReceiver());
            super.run();
        }
    }

    private final class DefaultFilterOutput implements FilterOutput {
        @Override
        public void out(String filterName, LogMessage[] newMessages, int numRemoved) {
            String msg;
            int loop_end = newMessages.length;
            for(int n=0; n < loop_end; n++) {
                msg = String.format("%s: [%s] %s", filterName, newMessages[n].data.tag ,newMessages[n].msg);
                System.out.println(msg);
            }
        }
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        TestMain tm = new TestMain();
    }
}

