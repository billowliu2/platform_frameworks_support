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

package android.arch.lifecycle;

import static android.arch.lifecycle.Lifecycle.DESTROYED;
import static android.arch.lifecycle.Lifecycle.STARTED;

import android.arch.core.executor.AppToolkitTaskExecutor;
import android.arch.core.internal.SafeIterableMap;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;

import java.util.Iterator;
import java.util.Map;

/**
 * LiveData is a data holder class that can be observed within a given lifecycle.
 * This means that an {@link Observer} can be added in a pair with a {@link LifecycleOwner}, and
 * this observer will be notified about modifications of the wrapped data only if the paired
 * LifecycleOwner is in active state. LifecycleOwner is considered as active, if its state is
 * {@link Lifecycle#STARTED} or {@link Lifecycle#RESUMED}. An observer added without a
 * LifecycleOwner is considered as always active and thus will be always notified about
 * modifications. For those observers, you should manually call {@link #removeObserver(Observer)}.
 *
 * <p> An observer added with a Lifecycle will be automatically removed if the corresponding
 * Lifecycle moves to {@link Lifecycle#DESTROYED} state. This is especially useful for activities
 * and fragments where they can safely observe LiveData and not worry about leaks: they will be
 * instantly unsubscribed when they are destroyed.
 *
 * <p>
 * In addition, LiveData has {@link LiveData#onActive()} and {@link LiveData#onInactive()} methods
 * to get notified when number of active {@link Observer}s change between 0 and 1.
 * This allows LiveData to release any heavy resources when it does not have any Observers that
 * are actively observing.
 * <p>
 * This class is designed to hold individual data fields of {@link ViewModel},
 * but can also be used for sharing data between different modules in your application
 * in a decoupled fashion.
 *
 * @param <T> The type of data hold by this instance
 * @see ViewModel
 */
@SuppressWarnings({"WeakerAccess", "unused"})
// TODO: Thread checks are too strict right now, we may consider automatically moving them to main
// thread.
public class LiveData<T> {
    private final Object mDataLock = new Object();
    static final int START_VERSION = -1;
    private static final Object NOT_SET = new Object();

    private static final LifecycleOwner ALWAYS_ON = new LifecycleOwner() {

        private LifecycleRegistry mRegistry = init();

        private LifecycleRegistry init() {
            LifecycleRegistry registry = new LifecycleRegistry(this);
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START);
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
            return registry;
        }

        @Override
        public Lifecycle getLifecycle() {
            return mRegistry;
        }
    };

    private SafeIterableMap<Observer<T>, LifecycleBoundObserver> mObservers =
            new SafeIterableMap<>();

    // how many observers are in active state
    private int mActiveCount = 0;
    private Object mData = NOT_SET;
    // when setData is called, we set the pending data and actual data swap happens on the main
    // thread
    private volatile Object mPendingData = NOT_SET;
    private int mVersion = START_VERSION;

    private boolean mDispatchingValue;
    @SuppressWarnings("FieldCanBeLocal")
    private boolean mDispatchInvalidated;
    private final Runnable mPostValueRunnable = new Runnable() {
        @Override
        public void run() {
            Object newValue;
            synchronized (mDataLock) {
                newValue = mPendingData;
                mPendingData = NOT_SET;
            }
            //noinspection unchecked
            setValue((T) newValue);
        }
    };

    private void considerNotify(LifecycleBoundObserver observer) {
        if (!observer.active) {
            return;
        }
        // Check latest state b4 dispatch. Maybe it changed state but we didn't get the event yet.
        //
        // we still first check observer.active to keep it as the entrance for events. So even if
        // the observer moved to an active state, if we've not received that event, we better not
        // notify for a more predictable notification order.
        if (!isActiveState(observer.owner.getLifecycle().getCurrentState())) {
            return;
        }
        if (observer.lastVersion >= mVersion) {
            return;
        }
        observer.lastVersion = mVersion;
        //noinspection unchecked
        observer.observer.onChanged((T) mData);
    }

    private void dispatchingValue(@Nullable LifecycleBoundObserver initiator) {
        if (mDispatchingValue) {
            mDispatchInvalidated = true;
            return;
        }
        mDispatchingValue = true;
        do {
            mDispatchInvalidated = false;
            if (initiator != null) {
                considerNotify(initiator);
                initiator = null;
            } else {
                for (Iterator<Map.Entry<Observer<T>, LifecycleBoundObserver>> iterator =
                        mObservers.iteratorWithAdditions(); iterator.hasNext(); ) {
                    considerNotify(iterator.next().getValue());
                    if (mDispatchInvalidated) {
                        break;
                    }
                }
            }
        } while (mDispatchInvalidated);
        mDispatchingValue = false;
    }

    /**
     * Adds the given observer to the observers list within the lifespan of the given
     * owner. The events are dispatched on the main thread. If LiveData already has data
     * set, it will be delivered to the observer.
     * <p>
     * The observer will only receive events if the owner is in {@link Lifecycle#STARTED}
     * or {@link Lifecycle#RESUMED} state (active).
     * <p>
     * If the owner moves to the {@link Lifecycle#DESTROYED} state, the observer will
     * automatically be removed.
     * <p>
     * When data changes while the {@code owner} is not active, it will not receive any updates.
     * If it becomes active again, it will receive the last available data automatically.
     * <p>
     * LiveData keeps a strong reference to the observer and the owner as long as the
     * given LifecycleOwner is not destroyed. When it is destroyed, LiveData removes references to
     * the observer &amp; the owner.
     * <p>
     * If the given owner is already in {@link Lifecycle#DESTROYED} state, LiveData
     * ignores the call.
     * <p>
     * If the given owner, observer tuple is already in the list, the call is ignored.
     * If the observer is already in the list with another owner, LiveData throws an
     * {@link IllegalArgumentException}.
     *
     * @param owner    The LifecycleOwner which controls the observer
     * @param observer The observer that will receive the events
     */
    @MainThread
    public void observe(LifecycleOwner owner, Observer<T> observer) {
        if (owner.getLifecycle().getCurrentState() == DESTROYED) {
            // ignore
            return;
        }
        LifecycleBoundObserver wrapper = new LifecycleBoundObserver(owner, observer);
        LifecycleBoundObserver existing = mObservers.putIfAbsent(observer, wrapper);
        if (existing != null && existing.owner != wrapper.owner) {
            throw new IllegalArgumentException("Cannot add the same observer"
                    + " with different lifecycles");
        }
        if (existing != null) {
            return;
        }
        owner.getLifecycle().addObserver(wrapper);
        wrapper.activeStateChanged(isActiveState(owner.getLifecycle().getCurrentState()));
    }

    /**
     * Adds the given observer to the observers list. This call is similar to
     * {@link LiveData#observe(LifecycleOwner, Observer)} with a LifecycleOwner, which
     * is always active. This means that the given observer will receive all events and will never
     * be automatically removed. You should manually call {@link #removeObserver(Observer)} to stop
     * observing this LiveData.
     * While LiveData has one of such observers, it will be considered
     * as active.
     * <p>
     * If the observer was already added with an owner to this LiveData, LiveData throws an
     * {@link IllegalArgumentException}.
     *
     * @param observer The observer that will receive the events
     */
    @MainThread
    public void observeForever(Observer<T> observer) {
        observe(ALWAYS_ON, observer);
    }

    /**
     * Removes the given observer from the observers list.
     *
     * @param observer The Observer to receive events.
     */
    @MainThread
    public void removeObserver(final Observer<T> observer) {
        assertMainThread("removeObserver");
        LifecycleBoundObserver removed = mObservers.remove(observer);
        if (removed == null) {
            return;
        }
        removed.owner.getLifecycle().removeObserver(removed);
        removed.activeStateChanged(false);
    }

    /**
     * Removes all observers that are tied to the given {@link LifecycleOwner}.
     *
     * @param owner The {@code LifecycleOwner} scope for the observers to be removed.
     */
    @MainThread
    public void removeObservers(final LifecycleOwner owner) {
        assertMainThread("removeObservers");
        for (Map.Entry<Observer<T>, LifecycleBoundObserver> entry : mObservers) {
            if (entry.getValue().owner == owner) {
                removeObserver(entry.getKey());
            }
        }
    }

    /**
     * Posts a task to a main thread to set the given value. So if you have a following code
     * executed in the main thread:
     * <pre class="prettyprint">
     * liveData.postValue("a");
     * liveData.setValue("b");
     * </pre>
     * The value "b" would be set at first and later the main thread would override it with
     * the value "a".
     * <p>
     * If you called this method multiple times before a main thread executed a posted task, only
     * the last value would be dispatched.
     *
     * @param value The new value
     */
    protected void postValue(T value) {
        boolean postTask;
        synchronized (mDataLock) {
            postTask = mPendingData == NOT_SET;
            mPendingData = value;
        }
        if (!postTask) {
            return;
        }
        AppToolkitTaskExecutor.getInstance().postToMainThread(mPostValueRunnable);
    }

    /**
     * Sets the value. If there are active observers, the value will be dispatched to them.
     * <p>
     * This method must be called from the main thread. If you need set a value from a background
     * thread, you can use {@link #postValue(Object)}
     *
     * @param value The new value
     */
    @MainThread
    protected void setValue(T value) {
        assertMainThread("setValue");
        mVersion++;
        mData = value;
        dispatchingValue(null);
    }

    /**
     * Returns the current value.
     * Note that calling this method on a background thread does not guarantee that the latest
     * value set will be received.
     *
     * @return the current value
     */
    @Nullable
    public T getValue() {
        // we do not return pending data here to be able to serve a consistent view to the main
        // thread.
        Object data = mData;
        if (mData != NOT_SET) {
            //noinspection unchecked
            return (T) mData;
        }
        return null;
    }

    int getVersion() {
        return mVersion;
    }

    /**
     * Called when the number of active observers change to 1 from 0.
     * <p>
     * This callback can be used to know that this LiveData is being used thus should be kept
     * up to date.
     */
    protected void onActive() {

    }

    /**
     * Called when the number of active observers change from 1 to 0.
     * <p>
     * This does not mean that there are no observers left, there may still be observers but their
     * lifecycle states is not {@link Lifecycle#STARTED} or {@link Lifecycle#STOPPED} (like an
     * Activity in the back stack).
     * <p>
     * You can get the number of observers via {@link #getObserverCount()}.
     */
    protected void onInactive() {

    }

    /**
     * Returns the number of observers.
     * <p>
     * If called on a background thread, the value might be unreliable.
     *
     * @return The number of observers
     */
    public int getObserverCount() {
        return mObservers.size();
    }

    /**
     * Returns the number of active observers.
     * <p>
     * If called on a background thread, the value might be unreliable.
     *
     * @return The number of active observers
     */
    public int getActiveObserverCount() {
        return mActiveCount;
    }

    class LifecycleBoundObserver implements LifecycleObserver {
        public final LifecycleOwner owner;
        public final Observer<T> observer;
        public boolean active;
        public int lastVersion = START_VERSION;

        LifecycleBoundObserver(LifecycleOwner owner, Observer<T> observer) {
            this.owner = owner;
            this.observer = observer;
        }

        @SuppressWarnings("unused")
        @OnLifecycleEvent(Lifecycle.Event.ON_ANY)
        void onStateChange() {
            if (owner.getLifecycle().getCurrentState() == DESTROYED) {
                removeObserver(observer);
                return;
            }
            // immediately set active state, so we'd never dispatch anything to inactive
            // owner
            activeStateChanged(isActiveState(owner.getLifecycle().getCurrentState()));

        }

        void activeStateChanged(boolean newActive) {
            if (newActive == active) {
                return;
            }
            active = newActive;
            boolean inInActive = mActiveCount == 0;
            mActiveCount += active ? 1 : -1;
            if (inInActive && active) {
                onActive();
            }
            if (!inInActive && !active) {
                onInactive();
            }
            if (active) {
                dispatchingValue(this);
            }
        }
    }

    static boolean isActiveState(@Lifecycle.State int state) {
        return state >= STARTED;
    }

    private void assertMainThread(String methodName) {
        if (!AppToolkitTaskExecutor.getInstance().isMainThread()) {
            throw new IllegalStateException("Cannot invoke " + methodName + " on a background"
                    + " thread. You can easily move the call to main thread using"
                    + " AppToolkitTaskExecutor.");
        }
    }
}
