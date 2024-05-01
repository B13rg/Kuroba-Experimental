package com.github.k1rakishou.chan.core.di.component.controller

import com.github.k1rakishou.chan.core.di.module.controller.ControllerModule
import com.github.k1rakishou.chan.core.di.module.controller.ControllerScopedViewModelFactoryModule
import com.github.k1rakishou.chan.core.di.module.controller.ControllerScopedViewModelModule
import com.github.k1rakishou.chan.core.di.scope.PerController
import com.github.k1rakishou.chan.features.album.AlbumViewControllerV2
import com.github.k1rakishou.chan.ui.controller.base.Controller
import dagger.BindsInstance
import dagger.Subcomponent

@PerController
@Subcomponent(
  modules = [
    ControllerModule::class,
    ControllerScopedViewModelFactoryModule::class,
    ControllerScopedViewModelModule::class
  ]
)
interface ControllerComponent : ControllerDependencies {
  fun inject(controller: Controller)
  fun inject(albumViewControllerV2: AlbumViewControllerV2)

  @Subcomponent.Builder
  interface Builder {
    @BindsInstance
    fun controller(controller: Controller): Builder
    @BindsInstance
    fun controllerModule(module: ControllerModule): Builder

    fun build(): ControllerComponent
  }
}