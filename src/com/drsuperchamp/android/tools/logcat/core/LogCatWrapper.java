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

package com.drsuperchamp.android.tools.logcat.core;

import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.Log.LogLevel;
import com.drsuperchamp.android.tools.logcat.core.AdbWrapper.ShellOutputReceiver;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class LogCatWrapper {
    private final int STRING_BUFFER_LENGTH;
    private static final Pattern sHeaderPattern = Pattern.compile(
            "^\\[\\s(\\d\\d-\\d\\d\\s\\d\\d:\\d\\d:\\d\\d\\.\\d+)" + //$NON-NLS-1$
            "\\s+(\\d*):(0x[0-9a-fA-F]+)\\s([VDIWE])/(.*)\\]$"); //$NON-NLS-1$
    private LogMessage[] mBuffer;
    private int mBufferEnd;
    private int mBufferStart;
    private LogFilter mDefaultFilter = null;
    private LogFilter[] mFilters = null;
    private LogColors mDefaultFilterColor = null;
    private LogMessageInfo mLastMessageInfo = null;
    private String mDevSerialNumber = null;
    private LogCatOutputReceiver mReceiver = null;

    /**
     * @param devSerialNumber
     * @param maxLogsToManage
     */
    public LogCatWrapper(String devSerialNumber, int maxLogsToManage) {
        STRING_BUFFER_LENGTH = maxLogsToManage;
        mBuffer = new LogMessage[STRING_BUFFER_LENGTH];
        mBufferStart = -1;
        mBufferEnd = -1;

        LogColors color = new LogColors();
        color.infoColor = new Color(0, 127, 0);
        color.debugColor = new Color(0, 0, 127);
        color.errorColor = new Color(255, 0, 0);
        color.warningColor = new Color(255, 127, 0);
        color.verboseColor = new Color(0, 0, 0);
        mDefaultFilterColor = color;
        mDefaultFilter = new LogFilter("Log");
        mDefaultFilter.setColors(mDefaultFilterColor);
        mDevSerialNumber = new String(devSerialNumber);
        mReceiver = new LogCatOutputReceiver();
    }

    /**
     * @param outputInterface
     */
    public void setDefaultFilterOutput(FilterOutput outputInterface) {
        mDefaultFilter.setOutput(outputInterface);
    }

    /**
     * @return
     */
    public ShellOutputReceiver getShellOutputReceiver() {
        return mReceiver;
    }

    /**
     * @param filterName
     * @param tag
     * @param pid
     * @param logLevel
     * @param colors
     * @param outInterface
     */
    public void addFilter(String filterName, String tag, String pid, String logLevel,
            LogColors colors, FilterOutput outInterface) {
        LogFilter newFilter = new LogFilter(filterName);
        newFilter.setTagMode(tag);
        if (pid != null && pid.length() > 0) {
            newFilter.setPidMode(Integer.parseInt(pid));
        } else {
            newFilter.setPidMode(-1);
        }
        if (logLevel != null && logLevel.length() > 0) {
            int level = -1;
            switch (logLevel.charAt(0)) {
                case 'E':
                    level = 6;
                    break;
                case 'W':
                    level = 5;
                    break;
                case 'I':
                    level = 4;
                    break;
                case 'D':
                    level = 3;
                    break;
                case 'V':
                    level = 2;
                    break;
            }
            newFilter.setLogLevel(level);
        } else {
            newFilter.setLogLevel(-1);
        }

        if (colors != null) {
            newFilter.setColors(colors);
        } else {
            newFilter.setColors(mDefaultFilterColor);
        }

        // add it to the array.
        if (mFilters != null && mFilters.length > 0) {
            LogFilter[] newFilters = new LogFilter[mFilters.length+1];
            System.arraycopy(mFilters, 0, newFilters, 0, mFilters.length);
            newFilters[mFilters.length] = newFilter;
            mFilters = newFilters;
        } else {
            mFilters = new LogFilter[1];
            mFilters[0] = newFilter;
        }

        if (outInterface != null)
            newFilter.setOutput(outInterface);
    }

    protected void addLog(String []lines) {
        if (lines.length > STRING_BUFFER_LENGTH) {
            //Log.e("LogCat", "Receiving more lines than STRING_BUFFER_LENGTH");
        }

        for (String line : lines) {
            // ignore empty lines.
            if (line.length() <= 0)
                continue;
            // check for header lines.
            Matcher matcher = sHeaderPattern.matcher(line);
            if (matcher.matches()) {
                // this is a header line, parse the header and keep it around.
                mLastMessageInfo = new LogMessageInfo();

                mLastMessageInfo.time = matcher.group(1);
                mLastMessageInfo.pidString = matcher.group(2);
                mLastMessageInfo.pid = Integer.valueOf(mLastMessageInfo.pidString);
                mLastMessageInfo.logLevel = LogLevel.getByLetterString(matcher.group(4));
                mLastMessageInfo.tag = matcher.group(5).trim();
            } else {
                // This is not a header line.
                // Create a new LogMessage and process it.
                LogMessage mc = new LogMessage();

                if (mLastMessageInfo == null) {
                    // The first line of output wasn't preceded
                    // by a header line; make something up so
                    // that users of mc.data don't NPE.
                    mLastMessageInfo = new LogMessageInfo();
                    mLastMessageInfo.time = "??-?? ??:??:??.???"; //$NON-NLS1$
                    mLastMessageInfo.pidString = "<unknown>"; //$NON-NLS1$
                    mLastMessageInfo.pid = 0;
                    mLastMessageInfo.logLevel = LogLevel.INFO;
                    mLastMessageInfo.tag = "<unknown>"; //$NON-NLS1$
                }

                // If someone printed a log message with
                // embedded '\n' characters, there will
                // one header line followed by multiple text lines.
                // Use the last header that we saw.
                mc.data = mLastMessageInfo;

                // tabs seem to display as only 1 tab so we replace the leading tabs
                // by 4 spaces.
                mc.msg = line.replaceAll("\t", "    "); //$NON-NLS-1$ //$NON-NLS-2$

                // process the new LogMessage.
                processNewMessage(mc);
            }
        }

        // TODO:
        // the circular buffer has been updated, let have the filter flush their
        // display with the new messages.
        if (mFilters != null) {
            for (LogFilter f : mFilters) {
                f.flush();
            }
        }

        if (mDefaultFilter != null) {
            mDefaultFilter.flush();
        }
    }

    private void processNewMessage(LogMessage newMessage) {
        // compute the index where the message goes.
        // was the buffer empty?
        int messageIndex = -1;
        if (mBufferStart == -1) {
            messageIndex = mBufferStart = 0;
            mBufferEnd = 1;
        } else {
            messageIndex = mBufferEnd;

            // check we aren't overwriting start
            if (mBufferEnd == mBufferStart) {
                mBufferStart = (mBufferStart + 1) % STRING_BUFFER_LENGTH;
            }

            // increment the next usable slot index
            mBufferEnd = (mBufferEnd + 1) % STRING_BUFFER_LENGTH;
        }

        LogMessage oldMessage = null;

        // record the message that was there before
        if (mBuffer[messageIndex] != null) {
            oldMessage = mBuffer[messageIndex];
        }

        // then add the new one
        mBuffer[messageIndex] = newMessage;

        // give the new message to every filters.
        boolean filtered = false;
        if (mFilters != null) {
            for (LogFilter f : mFilters) {
                filtered |= f.addMessage(newMessage, oldMessage);
            }
        }

        // Unlike eclipse's implementation, all filtered messages will be seen in the default filter.
        //if (filtered == false && mDefaultFilter != null) {
        if (mDefaultFilter != null) {
            mDefaultFilter.addMessage(newMessage, oldMessage);
        }
    }

    public static class LogMessage {
        public LogMessageInfo data;
        public String msg;

        @Override
        public String toString() {
            return data.time + ": "
                + data.logLevel + "/"
                + data.tag + "("
                + data.pidString + "): "
                + msg;
        }
    }

    public static class LogMessageInfo {
        public LogLevel logLevel;
        public int pid;
        public String pidString;
        public String tag;
        public String time;
    }

    public static class Color {
        int r;
        int g;
        int b;
        public Color(int r, int g, int b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    public static class LogColors {
        public Color infoColor;
        public Color debugColor;
        public Color errorColor;
        public Color warningColor;
        public Color verboseColor;
    }

    public static class LogFilter {
        public final static int MODE_PID = 0x01;
        public final static int MODE_TAG = 0x02;
        public final static int MODE_LEVEL = 0x04;

        private String mName;

        /**
         * Filtering mode. Value can be a mix of MODE_PID, MODE_TAG, MODE_LEVEL
         */
        private int mMode = 0;
        private int mModes[] = null;

        /**
         * pid used for filtering. Only valid if mMode is MODE_PID.
         */
        private int mPid;
        private int mPids[] = null;

        /** Single level log level as defined in Log.mLevelChar. Only valid
         * if mMode is MODE_LEVEL */
        private int mLogLevel;
        private int mLogLevels[] = null;

        /**
         * log tag filtering. Only valid if mMode is MODE_TAG
         */
        private String mTag;
        private String mTags[] = null;

        /** Temp keyword filtering */
        private String[] mTempKeywordFilters;

        /** temp pid filtering */
        private int mTempPid = -1;

        /** temp tag filtering */
        private String mTempTag;

        /** temp log level filtering */
        private int mTempLogLevel = -1;

        private LogColors mColors;
        private LogColors mColorss[] = null;

        private boolean mTempFilteringStatus = false;

        private final ArrayList<LogMessage> mMessages = new ArrayList<LogMessage>();
        private final ArrayList<LogMessage> mNewMessages = new ArrayList<LogMessage>();

        private int mRemovedMessageCount = 0;

        private FilterOutput mOutputInterface = null;

        /**
         * Creates a filter with a particular mode.
         * @param name The name to be displayed in the UI
         */
        public LogFilter(String name) {
            mName = name;
        }

        public LogFilter() {
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(mName);

            sb.append(':');
            sb.append(mMode);
            if ((mMode & MODE_PID) == MODE_PID) {
                sb.append(':');
                sb.append(mPid);
            }

            if ((mMode & MODE_LEVEL) == MODE_LEVEL) {
                sb.append(':');
                sb.append(mLogLevel);
            }

            if ((mMode & MODE_TAG) == MODE_TAG) {
                sb.append(':');
                sb.append(mTag);
            }

            return sb.toString();
        }

        public boolean loadFromString(String string) {
            String[] segments = string.split(":"); // $NON-NLS-1$
            int index = 0;

            // get the name
            mName = segments[index++];

            // get the mode
            mMode = Integer.parseInt(segments[index++]);

            if ((mMode & MODE_PID) == MODE_PID) {
                mPid = Integer.parseInt(segments[index++]);
            }

            if ((mMode & MODE_LEVEL) == MODE_LEVEL) {
                mLogLevel = Integer.parseInt(segments[index++]);
            }

            if ((mMode & MODE_TAG) == MODE_TAG) {
                mTag = segments[index++];
            }

            return true;
        }

        void setOutput(FilterOutput out) {
            mOutputInterface = out;
        }

        /** Sets the name of the filter. */
        void setName(String name) {
            mName = name;
        }

        /**
         * Returns the UI display name.
         */
        public String getName() {
            return mName;
        }

        /**
         * Resets the filtering mode to be 0 (i.e. no filter).
         */
        public void resetFilteringMode() {
            mMode = 0;
        }

        /**
         * Returns the current filtering mode.
         * @return A bitmask. Possible values are MODE_PID, MODE_TAG, MODE_LEVEL
         */
        public int getFilteringMode() {
            return mMode;
        }

        /**
         * Adds PID to the current filtering mode.
         * @param pid
         */
        public void setPidMode(int pid) {
            if (pid != -1) {
                mMode |= MODE_PID;
            } else {
                mMode &= ~MODE_PID;
            }
            mPid = pid;
        }

        /** Returns the pid filter if valid, otherwise -1 */
        public int getPidFilter() {
            if ((mMode & MODE_PID) == MODE_PID)
                return mPid;
            return -1;
        }

        public void setTagMode(String tag) {
            if (tag != null && tag.length() > 0) {
                mMode |= MODE_TAG;
            } else {
                mMode &= ~MODE_TAG;
            }
            mTag = tag;
        }

        public String getTagFilter() {
            if ((mMode & MODE_TAG) == MODE_TAG)
                return mTag;
            return null;
        }

        public void setLogLevel(int level) {
            if (level == -1) {
                mMode &= ~MODE_LEVEL;
            } else {
                mMode |= MODE_LEVEL;
                mLogLevel = level;
            }

        }

        public int getLogLevel() {
            if ((mMode & MODE_LEVEL) == MODE_LEVEL) {
                return mLogLevel;
            }

            return -1;
        }

        /**
         * Adds a new message and optionally removes an old message.
         * <p/>The new message is filtered through {@link #accept(LogMessage)}.
         * Calls to {@link #flush()} from a UI thread will display it (and other
         * pending messages) to the associated {@link Table}.
         * @param logMessage the MessageData object to filter
         * @return true if the message was accepted.
         */
        public boolean addMessage(LogMessage newMessage, LogMessage oldMessage) {
            synchronized (mMessages) {
                if (oldMessage != null) {
                    int index = mMessages.indexOf(oldMessage);
                    if (index != -1) {
                        // TODO check that index will always be -1 or 0, as only the oldest message is ever removed.
                        mMessages.remove(index);
                        mRemovedMessageCount++;
                    }

                    // now we look for it in mNewMessages. This can happen if the new message is added
                    // and then removed because too many messages are added between calls to #flush()
                    index = mNewMessages.indexOf(oldMessage);
                    if (index != -1) {
                        // TODO check that index will always be -1 or 0, as only the oldest message is ever removed.
                        mNewMessages.remove(index);
                    }
                }

                boolean filter = accept(newMessage);

                if (filter) {
                    // at this point the message is accepted, we add it to the list
                    mMessages.add(newMessage);
                    mNewMessages.add(newMessage);
                }

                return filter;
            }
        }

        /**
         * Removes all the items in the filter and its {@link Table}.
         */
        public void clear() {
            mRemovedMessageCount = 0;
            mNewMessages.clear();
            mMessages.clear();
        }

        /**
         * Filters a message.
         * @param logMessage the Message
         * @return true if the message is accepted by the filter.
         */
        boolean accept(LogMessage logMessage) {
            // do the regular filtering now
            if ((mMode & MODE_PID) == MODE_PID && mPid != logMessage.data.pid) {
                return false;
            }

            if ((mMode & MODE_TAG) == MODE_TAG && (
                    logMessage.data.tag == null ||
                    logMessage.data.tag.equals(mTag) == false)) {
                return false;
            }

            int msgLogLevel = logMessage.data.logLevel.getPriority();

            // test the temp log filtering first, as it replaces the old one
            if (mTempLogLevel != -1) {
                if (mTempLogLevel > msgLogLevel) {
                    return false;
                }
            } else if ((mMode & MODE_LEVEL) == MODE_LEVEL &&
                    mLogLevel > msgLogLevel) {
                return false;
            }

            // do the temp filtering now.
            if (mTempKeywordFilters != null) {
                String msg = logMessage.msg;

                for (String kw : mTempKeywordFilters) {
                    try {
                        if (msg.contains(kw) == false && msg.matches(kw) == false) {
                            return false;
                        }
                    } catch (PatternSyntaxException e) {
                        // if the string is not a valid regular expression,
                        // this exception is thrown.
                        return false;
                    }
                }
            }

            if (mTempPid != -1 && mTempPid != logMessage.data.pid) {
               return false;
            }

            if (mTempTag != null && mTempTag.length() > 0) {
                if (mTempTag.equals(logMessage.data.tag) == false) {
                    return false;
                }
            }

            return true;
        }

        /**
         * Takes all the accepted messages and display them.
         * This must be called from a UI thread.
         */
//        @UiThread
        public void flush() {
//            // if scroll bar is at the bottom, we will scroll
//            ScrollBar bar = mTable.getVerticalBar();
//            boolean scroll = bar.getMaximum() == bar.getSelection() + bar.getThumb();
//
//            // if we are not going to scroll, get the current first item being shown.
//            int topIndex = mTable.getTopIndex();
//
//            // disable drawing
//            mTable.setRedraw(false);
//
//            int totalCount = mNewMessages.size();
//
//            try {
//                // remove the items of the old messages.
//                for (int i = 0 ; i < mRemovedMessageCount && mTable.getItemCount() > 0 ; i++) {
//                    mTable.remove(0);
//                }
//
//                if (mUnreadCount > mTable.getItemCount()) {
//                    mUnreadCount = mTable.getItemCount();
//                }
//
//                // add the new items
//                for (int i = 0  ; i < totalCount ; i++) {
//                    LogMessage msg = mNewMessages.get(i);
//                    addTableItem(msg);
//                }
//            } catch (SWTException e) {
//                // log the error and keep going. Content of the logcat table maybe unexpected
//                // but at least ddms won't crash.
//                Log.e("LogFilter", e);
//            }
//
//            // redraw
//            mTable.setRedraw(true);
//
//            // scroll if needed, by showing the last item
//            if (scroll) {
//                totalCount = mTable.getItemCount();
//                if (totalCount > 0) {
//                    mTable.showItem(mTable.getItem(totalCount-1));
//                }
//            } else if (mRemovedMessageCount > 0) {
//                // we need to make sure the topIndex is still visible.
//                // Because really old items are removed from the list, this could make it disappear
//                // if we don't change the scroll value at all.
//
//                topIndex -= mRemovedMessageCount;
//                if (topIndex < 0) {
//                    // looks like it disappeared. Lets just show the first item
//                    mTable.showItem(mTable.getItem(0));
//                } else {
//                    mTable.showItem(mTable.getItem(topIndex));
//                }
//            }
//
//            // if this filter is not the current one, we update the tab text
//            // with the amount of unread message
//            if (mIsCurrentTabItem == false) {
//                mUnreadCount += mNewMessages.size();
//                totalCount = mTable.getItemCount();
//                if (mUnreadCount > 0) {
//                    mTabItem.setText(mName + " (" // $NON-NLS-1$
//                            + (mUnreadCount > totalCount ? totalCount : mUnreadCount)
//                            + ")");  // $NON-NLS-1$
//                } else {
//                    mTabItem.setText(mName);  // $NON-NLS-1$
//                }
//            }
//
            if (mOutputInterface != null) {
                mOutputInterface.out(mName, mNewMessages.toArray(new LogMessage[mNewMessages.size()]), mRemovedMessageCount);
            }
            mNewMessages.clear();
        }

        void setColors(LogColors colors) {
            mColors = colors;
        }

        void setTempKeywordFiltering(String[] segments) {
            mTempKeywordFilters = segments;
            mTempFilteringStatus = true;
        }

        void setTempPidFiltering(int pid) {
            mTempPid = pid;
            mTempFilteringStatus = true;
        }

        void setTempTagFiltering(String tag) {
            mTempTag = tag;
            mTempFilteringStatus = true;
        }

        void resetTempFiltering() {
            if (mTempPid != -1 || mTempTag != null || mTempKeywordFilters != null) {
                mTempFilteringStatus = true;
            }

            mTempPid = -1;
            mTempTag = null;
            mTempKeywordFilters = null;
        }

        void resetTempFilteringStatus() {
            mTempFilteringStatus = false;
        }

        boolean getTempFilterStatus() {
            return mTempFilteringStatus;
        }


        /**
         * Add a TableItem for the index-th item of the buffer
         * @param filter The index of the table in which to insert the item.
         */
//        private void addTableItem(LogMessage msg) {
//            TableItem item = new TableItem(mTable, SWT.NONE);
//            item.setText(0, msg.data.time);
//            item.setText(1, new String(new char[] { msg.data.logLevel.getPriorityLetter() }));
//            item.setText(2, msg.data.pidString);
//            item.setText(3, msg.data.tag);
//            item.setText(4, msg.msg);
//
//            // add the buffer index as data
//            item.setData(msg);
//
//            if (msg.data.logLevel == LogLevel.INFO) {
//                item.setForeground(mColors.infoColor);
//            } else if (msg.data.logLevel == LogLevel.DEBUG) {
//                item.setForeground(mColors.debugColor);
//            } else if (msg.data.logLevel == LogLevel.ERROR) {
//                item.setForeground(mColors.errorColor);
//            } else if (msg.data.logLevel == LogLevel.WARN) {
//                item.setForeground(mColors.warningColor);
//            } else {
//                item.setForeground(mColors.verboseColor);
//            }
//        }
    }

    /**
     * Base implementation of {@link IShellOutputReceiver}, that takes the raw data coming from the
     * socket, and convert it into {@link String} objects.
     * <p/>Additionally, it splits the string by lines.
     * <p/>Classes extending it must implement {@link #processNewLines(String[])} which receives
     * new parsed lines as they become available.
     */
    protected class LogCatOutputReceiver implements ShellOutputReceiver {
        private boolean isCancelled = false;

        private boolean mTrimLines = true;

        /** unfinished message line, stored for next packet */
        private String mUnfinishedLine = null;

        private final ArrayList<String> mArray = new ArrayList<String>();

        /**
         * Set the trim lines flag.
         * @param trim hether the lines are trimmed, or not.
         */
        public void setTrimLine(boolean trim) {
            mTrimLines = trim;
        }

        /* (non-Javadoc)
         * @see com.android.ddmlib.adb.IShellOutputReceiver#addOutput(
         *      byte[], int, int)
         */
        public final void addOutput(byte[] data, int offset, int length) {
            if (isCancelled() == false) {
                String s = null;
                try {
                    s = new String(data, offset, length, "ISO-8859-1"); //$NON-NLS-1$
                } catch (UnsupportedEncodingException e) {
                    // normal encoding didn't work, try the default one
                    s = new String(data, offset,length);
                }

                // ok we've got a string
                if (s != null) {
                    // if we had an unfinished line we add it.
                    if (mUnfinishedLine != null) {
                        s = mUnfinishedLine + s;
                        mUnfinishedLine = null;
                    }

                    // now we split the lines
                    mArray.clear();
                    int start = 0;
                    do {
                        int index = s.indexOf("\r\n", start); //$NON-NLS-1$

                        // if \r\n was not found, this is an unfinished line
                        // and we store it to be processed for the next packet
                        if (index == -1) {
                            mUnfinishedLine = s.substring(start);
                            break;
                        }

                        // so we found a \r\n;
                        // extract the line
                        String line = s.substring(start, index);
                        if (mTrimLines) {
                            line = line.trim();
                        }
                        mArray.add(line);

                        // move start to after the \r\n we found
                        start = index + 2;
                    } while (true);

                    if (mArray.size() > 0) {
                        // at this point we've split all the lines.
                        // make the array
                        String[] lines = mArray.toArray(new String[mArray.size()]);

                        // send it for final processing
                        processNewLines(lines);
                    }
                }
            }
        }

        /* (non-Javadoc)
         * @see com.android.ddmlib.adb.IShellOutputReceiver#flush()
         */
        public final void flush() {
            if (mUnfinishedLine != null) {
                processNewLines(new String[] { mUnfinishedLine });
            }

            done();
        }

        /**
         * Terminates the process. This is called after the last lines have been through
         * {@link #processNewLines(String[])}.
         */
        public void done() {
            // do nothing.
        }

        /**
         * Called when new lines are being received by the remote process.
         * <p/>It is guaranteed that the lines are complete when they are given to this method.
         * @param lines The array containing the new lines.
         */
        public void processNewLines(String[] lines) {
            addLog(lines);
        }

        @Override
        public boolean isCancelled() {
            return isCancelled;
        }

    }
}
