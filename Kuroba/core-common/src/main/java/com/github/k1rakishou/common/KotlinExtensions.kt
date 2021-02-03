package com.github.k1rakishou.common

import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.CharacterStyle
import android.view.View
import android.view.ViewGroup
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.*
import java.util.regex.Matcher
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashMap
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val ELLIPSIZE_SYMBOL: CharSequence = "…"

suspend fun OkHttpClient.suspendCall(request: Request): Response {
  return suspendCancellableCoroutine { continuation ->
    val call = newCall(request)

    continuation.invokeOnCancellation {
      Try { call.cancel() }.ignore()
    }

    call.enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        continuation.resumeWithException(e)
      }

      override fun onResponse(call: Call, response: Response) {
        continuation.resume(response)
      }
    })
  }
}

fun JsonReader.nextStringOrNull(): String? {
  if (peek() != JsonToken.STRING) {
    skipValue()
    return null
  }

  val value = nextString()
  if (value.isNullOrEmpty()) {
    return null
  }

  return value
}

fun JsonReader.nextIntOrNull(): Int? {
  if (peek() != JsonToken.NUMBER) {
    skipValue()
    return null
  }

  return nextInt()
}

fun JsonReader.nextBooleanOrNull(): Boolean? {
  if (peek() != JsonToken.BOOLEAN) {
    skipValue()
    return null
  }

  return nextBoolean()
}

inline fun <T : Any?> JsonReader.jsonObject(func: JsonReader.() -> T): T {
  beginObject()

  try {
    return func(this)
  } finally {
    var token = peek()

    while (token != JsonToken.END_OBJECT) {
      skipValue()
      token = peek()
    }

    endObject()
  }
}

inline fun <T> JsonReader.jsonArray(next: JsonReader.() -> T): T {
  beginArray()

  try {
    return next(this)
  } finally {
    var token = peek()

    while (token != JsonToken.END_ARRAY) {
      skipValue()
      token = peek()
    }

    endArray()
  }
}

inline fun <T, R> Iterable<T>.flatMapNotNull(transform: (T) -> Iterable<R>?): List<R> {
  return flatMapNotNullTo(ArrayList<R>(), transform)
}

inline fun <T, R, C : MutableCollection<in R>> Iterable<T>.flatMapNotNullTo(
  destination: C,
  transform: (T) -> Iterable<R>?
): C {
  this
    .mapNotNull { transform(it) }
    .forEach { destination.addAll(it) }
  return destination
}

inline fun <T, R> Collection<T>.flatMapIndexed(transform: (Int, T) -> Collection<R>): List<R> {
  val destination = mutableListOf<R>()

  for ((index, element) in this.withIndex()) {
    val list = transform(index, element)

    destination.addAll(list)
  }

  return destination
}

inline fun <T> MutableCollection<T>.removeIfKt(filter: (T) -> Boolean): Boolean {
  var removed = false
  val mutableIterator = iterator()

  while (mutableIterator.hasNext()) {
    if (filter.invoke(mutableIterator.next())) {
      mutableIterator.remove()
      removed = true
    }
  }

  return removed
}

inline fun <E> MutableList<E>.mutableIteration(func: (MutableIterator<E>, E) -> Boolean) {
  val iterator = this.iterator()

  while (iterator.hasNext()) {
    if (!func(iterator, iterator.next())) {
      return
    }
  }
}

public val <T> List<T>.lastIndexOrNull: Int?
  get() {
    if (this.isEmpty()) {
      return null
    }

    return this.size - 1
  }

@Suppress("RedundantAsync")
suspend fun <T> CoroutineScope.myAsync(
  context: CoroutineContext = EmptyCoroutineContext,
  func: suspend () -> T
): T {
  return supervisorScope {
    async(context = context) { func() }.await()
  }
}

inline fun <T> Collection<T>.forEachReverseIndexed(action: (index: Int, T) -> Unit): Unit {
  if (this.isEmpty()) {
    return
  }

  var index = this.size - 1

  for (item in this) {
    action(index--, item)
  }
}

inline fun <T, R> List<T>.highLowMap(mapper: (T) -> R): List<R> {
  if (isEmpty()) {
    return emptyList()
  }

  if (size == 1) {
    return listOf(mapper(first()))
  }

  var position = size / 2
  var index = 0
  var increment = true

  val resultList = mutableListWithCap<R>(size)

  var reachedLeftSide = false
  var reachedRightSize = false

  while (true) {
    val element = getOrNull(position)
    if (element == null) {
      if (reachedLeftSide && reachedRightSize) {
        break
      }

      if (position <= 0) {
        reachedLeftSide = true
      }

      if (position >= lastIndex) {
        reachedRightSize = true
      }
    }

    if (element != null) {
      resultList += mapper(element)
    }

    ++index

    if (increment) {
      position += index
    } else {
      position -= index
    }

    increment = increment.not()
  }

  return resultList
}

inline fun <T, R : Any, C : MutableCollection<in R>> Collection<T>.mapReverseIndexedNotNullTo(
  destination: C,
  transform: (index: Int, T) -> R?
): C {
  forEachReverseIndexed { index, element -> transform(index, element)?.let { destination.add(it) } }
  return destination
}

inline fun <T, R : Any> Collection<T>.mapReverseIndexedNotNull(transform: (index: Int, T) -> R?): List<R> {
  return mapReverseIndexedNotNullTo(ArrayList<R>(), transform)
}

/**
 * Forces the kotlin compiler to require handling of all branches in the "when" operator
 * */
val <T : Any?> T.exhaustive: T
  get() = this

fun Matcher.groupOrNull(group: Int): String? {
  return try {
    if (group < 0 || group > groupCount()) {
      return null
    }

    this.group(group)
  } catch (error: Throwable) {
    null
  }
}

/**
 * Not thread-safe!
 * */
fun <K, V> MutableMap<K, V>.putIfNotContains(key: K, value: V) {
  if (!this.containsKey(key)) {
    this[key] = value
  }
}

/**
 * Not thread-safe!
 * */
fun <K, V> HashMap<K, V>.putIfNotContains(key: K, value: V) {
  if (!this.containsKey(key)) {
    this[key] = value
  }
}

/**
 * Not thread-safe!
 * */
fun <K, V> TreeMap<K, V>.firstKeyOrNull(): K? {
  if (isEmpty()) {
    return null
  }

  return firstKey()
}

fun Throwable.errorMessageOrClassName(): String {
  if (!message.isNullOrBlank()) {
    return message!!
  }

  return this::class.java.name
}

fun Throwable.isExceptionImportant(): Boolean {
  return when (this) {
    is CancellationException -> false
    else -> true
  }
}

fun View.updateMargins(
  left: Int? = null,
  right: Int? = null,
  start: Int? = null,
  end: Int? = null,
  top: Int? = null,
  bottom: Int? = null
) {
  val layoutParams = layoutParams as? ViewGroup.MarginLayoutParams
    ?: return

  val newLeft = left ?: layoutParams.leftMargin
  val newRight = right ?: layoutParams.rightMargin
  val newStart = start ?: layoutParams.marginStart
  val newEnd = end ?: layoutParams.marginEnd
  val newTop = top ?: layoutParams.topMargin
  val newBottom = bottom ?: layoutParams.bottomMargin

  layoutParams.setMargins(
    newLeft,
    newTop,
    newRight,
    newBottom
  )

  layoutParams.marginStart = newStart
  layoutParams.marginEnd = newEnd
}

fun View.updatePaddings(
  left: Int? = null,
  right: Int? = null,
  top: Int? = null,
  bottom: Int? = null
) {
  val newLeft = left ?: paddingLeft
  val newRight = right ?: paddingRight
  val newTop = top ?: paddingTop
  val newBottom = bottom ?: paddingBottom

  setPadding(newLeft, newTop, newRight, newBottom)
}

fun View.updatePaddings(
  left: Int = paddingLeft,
  right: Int = paddingRight,
  top: Int = paddingTop,
  bottom: Int = paddingBottom
) {
  setPadding(left, top, right, bottom)
}

fun View.findChild(predicate: (View) -> Boolean): View? {
  if (predicate(this)) {
    return this
  }

  if (this !is ViewGroup) {
    return null
  }

  return findChildRecursively(this, predicate)
}

private fun findChildRecursively(viewGroup: ViewGroup, predicate: (View) -> Boolean): View? {
  for (index in 0 until viewGroup.childCount) {
    val child = viewGroup.getChildAt(index)
    if (predicate(child)) {
      return child
    }

    if (child is ViewGroup) {
      val result = findChildRecursively(child, predicate)
      if (result != null) {
        return result
      }
    }
  }

  return null
}

fun <T : View> View.findChildren(predicate: (View) -> Boolean): Set<T> {
  val children = hashSetOf<View>()

  if (predicate(this)) {
    children += this
  }

  findChildrenRecursively(children, this, predicate)
  return children as Set<T>
}

fun findChildrenRecursively(children: HashSet<View>, view: View, predicate: (View) -> Boolean) {
  if (view !is ViewGroup) {
    return
  }

  for (index in 0 until view.childCount) {
    val child = view.getChildAt(index)
    if (predicate(child)) {
      children += child
    }

    if (child is ViewGroup) {
      findChildrenRecursively(children, child, predicate)
    }
  }
}

fun View.updateHeight(newHeight: Int) {
  val updatedLayoutParams = layoutParams
  updatedLayoutParams.height = newHeight
  layoutParams = updatedLayoutParams
}

fun String.ellipsizeEnd(maxLength: Int): String {
  val minStringLength = 5

  if (maxLength < minStringLength) {
    return this
  }

  if (this.length <= maxLength) {
    return this
  }

  return this.take(maxLength - ELLIPSIZE_SYMBOL.length) + ELLIPSIZE_SYMBOL
}

fun CharSequence.ellipsizeEnd(maxLength: Int): CharSequence {
  val minStringLength = 5

  if (maxLength < minStringLength) {
    return this
  }

  if (this.length <= maxLength) {
    return this
  }

  return TextUtils.concat(this.take(maxLength - ELLIPSIZE_SYMBOL.length), ELLIPSIZE_SYMBOL)
}

@Suppress("ReplaceSizeCheckWithIsNotEmpty", "NOTHING_TO_INLINE")
@OptIn(ExperimentalContracts::class)
inline fun CharSequence?.isNotNullNorEmpty(): Boolean {
  contract {
    returns(true) implies (this@isNotNullNorEmpty != null)
  }

  return this != null && this.length > 0
}

suspend fun <T> CompletableDeferred<T>.awaitSilently(defaultValue: T): T {
  return try {
    await()
  } catch (ignored: CancellationException) {
    defaultValue
  }
}

suspend fun Deferred<*>.awaitSilently() {
  try {
    await()
  } catch (ignored: CancellationException) {
    // no-op
  }
}

suspend fun CompletableDeferred<*>.awaitSilently() {
  try {
    await()
  } catch (ignored: CancellationException) {
    // no-op
  }
}

fun View.resetClickListener() {
  setOnClickListener(null)

  // setOnClickListener sets isClickable to true even when the callback is null
  // (which is absolutely not obvious)
  isClickable = false
}

fun View.resetLongClickListener() {
  setOnLongClickListener(null)

  // setOnLongClickListener sets isClickable to true even when the callback is null
  // (which is absolutely not obvious)
  isClickable = false
}

fun safeCapacity(initialCapacity: Int): Int {
  return if (initialCapacity < 16) {
    16
  } else {
    initialCapacity
  }
}

inline fun <T> mutableListWithCap(initialCapacity: Int): MutableList<T> {
  return ArrayList(safeCapacity(initialCapacity))
}

inline fun <T> mutableListWithCap(collection: Collection<*>): MutableList<T> {
  return ArrayList(safeCapacity(collection.size))
}

inline fun <K, V> mutableMapWithCap(initialCapacity: Int): MutableMap<K, V> {
  return HashMap(safeCapacity(initialCapacity))
}

inline fun <K, V> mutableMapWithCap(collection: Collection<*>): MutableMap<K, V> {
  return HashMap(safeCapacity(collection.size))
}

inline fun <K, V> linkedMapWithCap(initialCapacity: Int): LinkedHashMap<K, V> {
  return LinkedHashMap(safeCapacity(initialCapacity))
}

inline fun <K, V> linkedMapWithCap(collection: Collection<*>): LinkedHashMap<K, V> {
  return LinkedHashMap(safeCapacity(collection.size))
}

inline fun <T> hashSetWithCap(initialCapacity: Int): HashSet<T> {
  return HashSet(safeCapacity(initialCapacity))
}

inline fun <T> hashSetWithCap(collection: Collection<*>): HashSet<T> {
  return HashSet(safeCapacity(collection.size))
}

public inline fun <T> Iterable<T>.sumByLong(selector: (T) -> Long): Long {
  var sum = 0L
  for (element in this) {
    sum += selector(element)
  }
  return sum
}

fun SpannableStringBuilder.setSpanSafe(span: CharacterStyle, start: Int, end: Int, flags: Int) {
  setSpan(
    span,
    start.coerceAtLeast(0),
    end.coerceAtMost(this.length),
    flags
  )
}

fun SpannableString.setSpanSafe(span: CharacterStyle, start: Int, end: Int, flags: Int) {
  setSpan(
    span,
    start.coerceAtLeast(0),
    end.coerceAtMost(this.length),
    flags
  )
}