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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.arch.core.executor.AppToolkitTaskExecutor;
import android.arch.lifecycle.util.InstantTaskExecutor;
import android.support.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@SuppressWarnings("unchecked")
@SmallTest
@RunWith(JUnit4.class)
public class MediatorLiveDataTest {

    private LifecycleOwner mOwner;
    private LifecycleRegistry mRegistry;
    private MediatorLiveData<String> mMediator;
    private LiveData<String> mSource;
    private boolean mSourceActive;

    @Before
    public void setup() {
        mOwner = mock(LifecycleOwner.class);
        mRegistry = new LifecycleRegistry(mOwner);
        when(mOwner.getLifecycle()).thenReturn(mRegistry);
        mMediator = new MediatorLiveData<>();
        mSource = new LiveData<String>() {
            @Override
            protected void onActive() {
                mSourceActive = true;
            }

            @Override
            protected void onInactive() {
                mSourceActive = false;
            }
        };
        mSourceActive = false;
        mMediator.observe(mOwner, mock(Observer.class));
        mRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
    }

    @Before
    public void swapExecutorDelegate() {
        AppToolkitTaskExecutor.getInstance().setDelegate(new InstantTaskExecutor());
    }

    @Test
    public void testSingleDelivery() {
        Observer observer = mock(Observer.class);
        mMediator.addSource(mSource, observer);
        mSource.setValue("flatfoot");
        verify(observer).onChanged("flatfoot");
        mRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
        reset(observer);
        verify(observer, never()).onChanged(any());
    }

    @Test
    public void testChangeWhileInactive() {
        Observer observer = mock(Observer.class);
        mMediator.addSource(mSource, observer);
        mMediator.observe(mOwner, mock(Observer.class));
        mSource.setValue("one");
        verify(observer).onChanged("one");
        mRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
        reset(observer);
        mSource.setValue("flatfoot");
        mRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
        verify(observer).onChanged("flatfoot");
    }


    @Test
    public void testAddSourceToActive() {
        mSource.setValue("flatfoot");
        Observer observer = mock(Observer.class);
        mMediator.addSource(mSource, observer);
        verify(observer).onChanged("flatfoot");
    }

    @Test
    public void testAddSourceToInActive() {
        mSource.setValue("flatfoot");
        mRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
        Observer observer = mock(Observer.class);
        mMediator.addSource(mSource, observer);
        verify(observer, never()).onChanged(any());
        mRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
        verify(observer).onChanged("flatfoot");
    }

    @Test
    public void testRemoveSource() {
        mSource.setValue("flatfoot");
        Observer observer = mock(Observer.class);
        mMediator.addSource(mSource, observer);
        verify(observer).onChanged("flatfoot");
        mMediator.removeSource(mSource);
        reset(observer);
        mSource.setValue("failure");
        verify(observer, never()).onChanged(any());
    }

    @Test
    public void testSourceInactive() {
        Observer observer = mock(Observer.class);
        mMediator.addSource(mSource, observer);
        assertThat(mSourceActive, is(true));
        mRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
        assertThat(mSourceActive, is(false));
        mRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
        assertThat(mSourceActive, is(true));
    }

    @Test
    public void testNoLeakObserver() {
        // Imitates a destruction of a ViewModel: a listener of LiveData is destroyed,
        // a reference to MediatorLiveData is cleaned up. In this case we shouldn't leak
        // MediatorLiveData as an observer of mSource.
        assertThat(mSource.getObserverCount(), is(0));
        Observer observer = mock(Observer.class);
        mMediator.addSource(mSource, observer);
        assertThat(mSource.getObserverCount(), is(1));
        mRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
        mRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
        mMediator = null;
        assertThat(mSource.getObserverCount(), is(0));
    }

    @Test
    public void testMultipleSources() {
        Observer observer1 = mock(Observer.class);
        mMediator.addSource(mSource, observer1);
        MutableLiveData<Integer> source2 = new MutableLiveData<>();
        Observer observer2 = mock(Observer.class);
        mMediator.addSource(source2, observer2);
        mSource.setValue("flatfoot");
        verify(observer1).onChanged("flatfoot");
        verify(observer2, never()).onChanged(any());
        reset(observer1, observer2);
        source2.setValue(1703);
        verify(observer1, never()).onChanged(any());
        verify(observer2).onChanged(1703);
        reset(observer1, observer2);
        mRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
        mSource.setValue("failure");
        source2.setValue(0);
        verify(observer1, never()).onChanged(any());
        verify(observer2, never()).onChanged(any());
    }
}
