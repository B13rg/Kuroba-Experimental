package com.github.k1rakishou.chan.features.toolbar_v2.state.container

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.github.k1rakishou.chan.features.toolbar_v2.ToolbarMenu
import com.github.k1rakishou.chan.features.toolbar_v2.ToolbarMenuItem
import com.github.k1rakishou.chan.features.toolbar_v2.state.IKurobaToolbarParams
import com.github.k1rakishou.chan.features.toolbar_v2.state.KurobaToolbarSubState
import com.github.k1rakishou.chan.features.toolbar_v2.state.ToolbarStateKind

@Immutable
class KurobaContainerToolbarParams : IKurobaToolbarParams {
  override val kind: ToolbarStateKind = ToolbarStateKind.Container
}

@Stable
class KurobaContainerToolbarSubState(
  params: KurobaContainerToolbarParams = KurobaContainerToolbarParams()
) : KurobaToolbarSubState() {
  override val kind: ToolbarStateKind = params.kind

  override val leftMenuItem: ToolbarMenuItem? = null

  override val rightToolbarMenu: ToolbarMenu? = null

  override fun update(params: IKurobaToolbarParams) {
    // no-op
  }

  override fun onPushed() {
    // no-op
  }

  override fun onPopped() {
    // no-op
  }

  override fun toString(): String {
    return "KurobaContainerToolbarSubState()"
  }

}