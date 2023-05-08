/*
 * Copyright © 2018 TransTracks. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.drspaceboo.transtracks.util

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.schedulers.TestScheduler

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

inline fun <reified T : Any> io.reactivex.Observable<T>.toV3(): Observable<T> =
    Observable.create { emitter ->
        val disposable: io.reactivex.disposables.Disposable = this@toV3.subscribe(
            /* onNext = */ { emitter.onNext(it) },
            /* onError = */ { emitter.onError(it) },
            /* onComplete = */ { emitter.onComplete() }
        )
        emitter.setCancellable {
            disposable.dispose()
        }
    }

sealed class RxSchedulerMode {
    object REAL : RxSchedulerMode()
    object TRAMPOLINE : RxSchedulerMode()
    data class TEST(val testScheduler: TestScheduler) : RxSchedulerMode()
}

object RxSchedulers {
    var mode: RxSchedulerMode = RxSchedulerMode.REAL

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

object Observables {
    inline fun <T1 : Any, T2 : Any, R : Any> combineLatest(
        source1: Observable<T1>, source2: Observable<T2>, crossinline combineFunction: (T1, T2) -> R
    ) = Observable.combineLatest(source1, source2) { t1: T1, t2: T2 -> combineFunction(t1, t2) }

    inline fun <T1 : Any, T2 : Any, T3 : Any, R : Any> combineLatest(
        source1: Observable<T1>,
        source2: Observable<T2>,
        source3: Observable<T3>,
        crossinline combineFunction: (T1, T2, T3) -> R
    ) = Observable.combineLatest(source1, source2, source3) { t1: T1, t2: T2, t3: T3 ->
        combineFunction(t1, t2, t3)
    }

    inline fun <T1 : Any, T2 : Any, T3 : Any, T4 : Any, R : Any> combineLatest(
        source1: Observable<T1>,
        source2: Observable<T2>,
        source3: Observable<T3>,
        source4: Observable<T4>,
        crossinline combineFunction: (T1, T2, T3, T4) -> R
    ) = Observable.combineLatest(
        source1, source2, source3, source4
    ) { t1: T1, t2: T2, t3: T3, t4: T4 ->
        combineFunction(t1, t2, t3, t4)
    }

    inline fun <T1 : Any, T2 : Any, R : Any> zip(
        source1: Observable<T1>, source2: Observable<T2>, crossinline zipper: (T1, T2) -> R
    ) = Observable.zip(source1, source2) { t1: T1, t2: T2 -> zipper(t1, t2) }
}