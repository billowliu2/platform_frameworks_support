/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.arch.background.workmanager.constraints.trackers;

import android.arch.background.workmanager.constraints.listeners.StorageNotLowListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * A {@link BroadcastReceiver} for storage level.
 */

public class StorageNotLowTracker extends ConstraintTracker<StorageNotLowListener> {

    private Boolean mIsStorageNotLow;

    public StorageNotLowTracker(Context context) {
        super(context);
    }

    @Override
    public void setUpInitialState(StorageNotLowListener listener) {
        if (mIsStorageNotLow == null) {
            Intent intent = mAppContext.registerReceiver(null, getIntentFilter());
            if (intent == null || intent.getAction() == null) {
                return;
            }

            switch (intent.getAction()) {
                case Intent.ACTION_DEVICE_STORAGE_OK:
                    mIsStorageNotLow = true;
                    break;
                case Intent.ACTION_DEVICE_STORAGE_LOW:
                    mIsStorageNotLow = false;
                    break;
            }
        }

        if (mIsStorageNotLow != null) {
            listener.setStorageNotLow(mIsStorageNotLow);
        }
    }

    @Override
    public IntentFilter getIntentFilter() {
        // In API 26+, DEVICE_STORAGE_OK/LOW are deprecated and are no longer sent to
        // manifest-defined BroadcastReceivers. Since we are registering our receiver manually, this
        // is currently okay. This may change in future versions, so this will need to be monitored.
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_OK);
        intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_LOW);
        return intentFilter;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return; // Should never happen since the IntentFilter was configured.
        }

        switch (intent.getAction()) {
            case Intent.ACTION_DEVICE_STORAGE_OK:
                setIsStorageNotLowAndNotify(true);
                break;
            case Intent.ACTION_DEVICE_STORAGE_LOW:
                setIsStorageNotLowAndNotify(false);
                break;
        }
    }

    private void setIsStorageNotLowAndNotify(boolean isStorageNotLow) {
        if (mIsStorageNotLow != isStorageNotLow) {
            mIsStorageNotLow = isStorageNotLow;
            for (StorageNotLowListener listener : mListeners) {
                listener.setStorageNotLow(mIsStorageNotLow);
            }
        }
    }
}
