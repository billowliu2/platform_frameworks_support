/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.work.worker;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import androidx.work.Result;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * A Worker that returns {@code null} for a {@link Result}.
 */
public class ReturnNullResultWorker extends Worker {

    public ReturnNullResultWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @Override
    public @NonNull Result doWork() {
        Log.d("ReturnNullResultWorker", "ReturnNullResultWorker Ran!");
        return null;
    }
}