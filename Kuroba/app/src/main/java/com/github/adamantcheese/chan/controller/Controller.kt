/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.controller

import android.content.Context
import android.content.res.Configuration
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import com.github.adamantcheese.chan.StartActivity
import com.github.adamantcheese.chan.controller.transition.FadeInTransition
import com.github.adamantcheese.chan.controller.transition.FadeOutTransition
import com.github.adamantcheese.chan.ui.controller.navigation.DoubleNavigationController
import com.github.adamantcheese.chan.ui.controller.navigation.NavigationController
import com.github.adamantcheese.chan.ui.toolbar.NavigationItem
import com.github.adamantcheese.chan.ui.toolbar.Toolbar
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.Logger
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.plus
import java.util.*

abstract class Controller(@JvmField var context: Context) {

  lateinit var view: ViewGroup

  @JvmField
  var navigation = NavigationItem()
  @JvmField
  var parentController: Controller? = null
  @JvmField
  var childControllers: MutableList<Controller> = ArrayList()

  // NavigationControllers members
  @JvmField
  var previousSiblingController: Controller? = null
  @JvmField
  var navigationController: NavigationController? = null
  @JvmField
  var doubleNavigationController: DoubleNavigationController? = null

  /**
   * Controller that this controller is presented by.
   */
  @JvmField
  var presentedByController: Controller? = null

  /**
   * Controller that this controller is presenting.
   */
  @JvmField
  var presentingThisController: Controller? = null

  val top: Controller?
    get() = if (childControllers.size > 0) {
      childControllers[childControllers.size - 1]
    } else {
      null
    }

  open val toolbar: Toolbar?
    get() = null

  @JvmField
  var alive = false

  protected var compositeDisposable = CompositeDisposable()
  protected var mainScope = MainScope() + CoroutineName("Controller")

  private var shown = false

  fun requireToolbar(): Toolbar = requireNotNull(toolbar) {
    "Toolbar was not set"
  }
  fun requireNavController(): NavigationController = requireNotNull(navigationController) {
    "navigationController was not set"
  }

  @CallSuper
  open fun onCreate() {
    alive = true

    if (LOG_STATES) {
      Logger.test(javaClass.simpleName + " onCreate")
    }
  }

  @CallSuper
  open fun onShow() {
    shown = true

    if (LOG_STATES) {
      Logger.test(javaClass.simpleName + " onShow")
    }

    view.visibility = View.VISIBLE

    for (controller in childControllers) {
      if (!controller.shown) {
        controller.onShow()
      }
    }
  }

  @CallSuper
  open fun onHide() {
    shown = false

    if (LOG_STATES) {
      Logger.test(javaClass.simpleName + " onHide")
    }

    view.visibility = View.GONE

    for (controller in childControllers) {
      if (controller.shown) {
        controller.onHide()
      }
    }
  }

  @CallSuper
  open fun onDestroy() {
    alive = false
    compositeDisposable.clear()
    mainScope.cancel()

    if (LOG_STATES) {
      Logger.test(javaClass.simpleName + " onDestroy")
    }

    while (childControllers.size > 0) {
      removeChildController(childControllers[0])
    }

    if (AndroidUtils.removeFromParentView(view)) {
      if (LOG_STATES) {
        Logger.test(javaClass.simpleName + " view removed onDestroy")
      }
    }
  }

  fun addChildController(controller: Controller) {
    childControllers.add(controller)
    controller.parentController = this

    if (doubleNavigationController != null) {
      controller.doubleNavigationController = doubleNavigationController
    }

    if (navigationController != null) {
      controller.navigationController = navigationController
    }

    controller.onCreate()
  }

  fun removeChildController(controller: Controller) {
    controller.onDestroy()
    childControllers.remove(controller)
  }

  fun attachToParentView(parentView: ViewGroup?) {
    if (view.parent != null) {
      if (LOG_STATES) {
        Logger.test(javaClass.simpleName + " view removed")
      }

      AndroidUtils.removeFromParentView(view)
    }

    if (parentView != null) {
      if (LOG_STATES) {
        Logger.test(javaClass.simpleName + " view attached")
      }

      attachToView(parentView)
    }
  }

  open fun onConfigurationChanged(newConfig: Configuration) {
    for (controller in childControllers) {
      controller.onConfigurationChanged(newConfig)
    }
  }

  open fun dispatchKeyEvent(event: KeyEvent?): Boolean {
    for (i in childControllers.indices.reversed()) {
      val controller = childControllers[i]
      if (controller.dispatchKeyEvent(event)) {
        return true
      }
    }

    return false
  }

  open fun onBack(): Boolean {
    for (index in childControllers.indices.reversed()) {
      val controller = childControllers[index]
      if (controller.onBack()) {
        return true
      }
    }
    return false
  }

  @JvmOverloads
  open fun presentController(controller: Controller, animated: Boolean = true) {
    val contentView = (context as StartActivity).contentView
    presentingThisController = controller

    controller.presentedByController = this
    controller.onCreate()
    controller.attachToView(contentView)
    controller.onShow()

    if (animated) {
      val transition = FadeInTransition()
      transition.to = controller
      transition.perform()
    }

    (context as StartActivity).pushController(controller)
  }

  fun isAlreadyPresenting(predicate: Function1<Controller, Boolean>): Boolean {
    return (context as StartActivity).isControllerAdded(predicate)
  }

  open fun stopPresenting() {
    stopPresenting(true)
  }

  open fun stopPresenting(animated: Boolean) {
    if (animated) {
      val transition = FadeOutTransition()
      transition.from = this
      transition.setCallback { finishPresenting() }
      transition.perform()
    } else {
      finishPresenting()
    }

    (context as StartActivity).popController(this)
    presentedByController?.presentingThisController = null
  }

  private fun finishPresenting() {
    onHide()
    onDestroy()
  }

  private fun attachToView(parentView: ViewGroup) {
    var params = view.layoutParams
    if (params == null) {
      params = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
    } else {
      params.width = ViewGroup.LayoutParams.MATCH_PARENT
      params.height = ViewGroup.LayoutParams.MATCH_PARENT
    }

    view.layoutParams = params
    parentView.addView(view, view.layoutParams)
  }

  companion object {
    private const val LOG_STATES = false
  }

}