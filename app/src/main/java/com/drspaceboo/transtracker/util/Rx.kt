/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracker.util

import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.schedulers.TestScheduler

/**
 * disposable += observable.subscribe()
 */
operator fun CompositeDisposable.plusAssign(disposable: Disposable) {
    add(disposable)
}

/**
 * Filters the items emitted by an Observable, only emitting those of the specified type.
 */
inline fun <reified R : Any> Observable<*>.ofType(): Observable<R> = ofType(R::class.java)

sealed class RxSchedulerMode {
    object REAL : RxSchedulerMode()
    object TRAMPOLINE : RxSchedulerMode()
    data class TEST(val testScheduler: TestScheduler) : RxSchedulerMode()
}

object RxSchedulers {
    var mode: RxSchedulerMode = RxSchedulerMode.TRAMPOLINE

    fun main(): Scheduler = when (mode) {
        is RxSchedulerMode.REAL -> AndroidSchedulers.mainThread()
        is RxSchedulerMode.TRAMPOLINE -> Schedulers.trampoline()
        is RxSchedulerMode.TEST -> (mode as RxSchedulerMode.TEST).testScheduler
    }

    fun io(): Scheduler = when (mode) {
        is RxSchedulerMode.REAL -> Schedulers.io()
        is RxSchedulerMode.TRAMPOLINE -> Schedulers.trampoline()
        is RxSchedulerMode.TEST -> (mode as RxSchedulerMode.TEST).testScheduler
    }

    fun computation(): Scheduler = when (mode) {
        is RxSchedulerMode.REAL -> Schedulers.computation()
        is RxSchedulerMode.TRAMPOLINE -> Schedulers.trampoline()
        is RxSchedulerMode.TEST -> (mode as RxSchedulerMode.TEST).testScheduler
    }

    fun trampoline(): Scheduler = when (mode) {
        is RxSchedulerMode.TEST -> (mode as RxSchedulerMode.TEST).testScheduler
        else -> Schedulers.trampoline()
    }
}
