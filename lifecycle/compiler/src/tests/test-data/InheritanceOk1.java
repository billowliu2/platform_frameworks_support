/*
 * Copyright (C) 2016 The Android Open Source Project
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

package foo;

import static android.arch.lifecycle.Lifecycle.Event.ON_STOP;

import android.arch.lifecycle.Lifecycle.Event;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.OnLifecycleEvent;

class Base1 {
    @OnLifecycleEvent(ON_STOP)
    public void onStop(LifecycleOwner provider, Event event) {
    }
}

class Proxy extends Base1 {
}

class Derived1 extends Proxy {
    @OnLifecycleEvent(ON_STOP)
    public void onStop2(LifecycleOwner provider, Event event) {
    }
}

class Derived2 extends Proxy {
    @OnLifecycleEvent(ON_STOP)
    public void onStop2(LifecycleOwner provider, Event event) {
    }
}

class Base2 {
    @OnLifecycleEvent(ON_STOP)
    public void onStop(LifecycleOwner provider, Event event) {
    }
}

class Derived3 extends Base2 {
    @OnLifecycleEvent(ON_STOP)
    public void onStop2(LifecycleOwner provider, Event event) {
    }
}
