package com.froxynetwork.servermanager.scheduler;

import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Supplier;

import org.junit.Test;

/**
 * MIT License
 *
 * Copyright (c) 2020 FroxyNetwork
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * 
 * @author 0ddlyoko
 */
//@RunWith(MockitoJUnitRunner.class)
public class SchedulerTest {

	@SuppressWarnings("unchecked")
	@Test
	public void testAdd() {
		Scheduler.start();
		Supplier<Boolean> sup = mock(Supplier.class);
		Runnable err = mock(Runnable.class);
		when(sup.get()).thenReturn(true);
		// Add in scheduler
		Scheduler.add(sup, err);

		Scheduler.stop();
		// Check that this method has never been called
		verify(err, never()).run();
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testAddError() {
		Scheduler.start();
		Supplier<Boolean> sup = mock(Supplier.class);
		Runnable err = mock(Runnable.class);
		when(sup.get()).thenReturn(false);
		// Add in scheduler
		Scheduler.add(sup, err);

		Scheduler.stop();
		// Check that this method has been called one time
		verify(err).run();
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testAddFalseTrue() {
		Scheduler.start();
		Supplier<Boolean> sup = mock(Supplier.class);
		Runnable err = mock(Runnable.class);
		when(sup.get()).thenReturn(false, false, true);
		// Add in scheduler
		Scheduler.add(sup, err);
		// Check that this method has been called 3 times
		verify(sup, after(3100).times(3)).get();

		Scheduler.stop();
		// Check that this method has never been called
		verify(err, never()).run();
	}
}
