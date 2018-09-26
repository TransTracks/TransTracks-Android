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

import java.io.Serializable

/**
 * Represents a set of four values
 *
 * There is no meaning attached to values in this class, it can be used for any purpose.
 * Quadruple exhibits value semantics, i.e. two quadruple are equal if all four components are equal.
 *
 * @param A type of the first value.
 * @param B type of the second value.
 * @param C type of the third value.
 * @param D type of the forth value.
 * @property first First value.
 * @property second Second value.
 * @property third Third value.
 * @property forth Forth value.
 */
data class Quadruple<out A, out B, out C, out D>(val first: A, val second: B, val third: C,
                                                 val forth: D) : Serializable {
    /**
     * Returns string representation of the [Quadruple] including its [first], [second], [third] and
     * [forth] values.
     */
    override fun toString(): String = "($first, $second, $third, $forth)"
}

/**
 * Converts this triple into a list.
 */
fun <T> Quadruple<T, T, T, T>.toList(): List<T> = listOf(first, second, third, forth)
