package com.github.k1rakishou.chan.features.toolbar_v2.state.reply

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.chan.features.toolbar_v2.ToolbarMenu
import com.github.k1rakishou.chan.features.toolbar_v2.ToolbarMenuItem
import com.github.k1rakishou.chan.features.toolbar_v2.state.IKurobaToolbarParams
import com.github.k1rakishou.chan.features.toolbar_v2.state.IKurobaToolbarState
import com.github.k1rakishou.chan.features.toolbar_v2.state.ToolbarStateKind
import com.github.k1rakishou.chan.features.toolbar_v2.state.search.KurobaSearchToolbarParams
import com.github.k1rakishou.chan.features.toolbar_v2.state.selection.KurobaSelectionToolbarParams

data class KurobaReplyToolbarParams(
  val toolbarMenu: ToolbarMenu? = null
) : IKurobaToolbarParams {
  override val kind: ToolbarStateKind = ToolbarStateKind.Reply
}

class KurobaReplyToolbarState(
  params: KurobaSearchToolbarParams = KurobaSearchToolbarParams()
) : IKurobaToolbarState {
  private val _toolbarMenu = mutableStateOf<ToolbarMenu?>(params.toolbarMenu)
  val toolbarMenu: State<ToolbarMenu?>
    get() = _toolbarMenu

  override val kind: ToolbarStateKind = params.kind

  override val leftMenuItem: ToolbarMenuItem? = null

  override val rightToolbarMenu: ToolbarMenu?
    get() = _toolbarMenu.value

  override fun update(params: IKurobaToolbarParams) {
    params as KurobaSelectionToolbarParams

    _toolbarMenu.value = params.toolbarMenu
  }

  override fun updateFromState(toolbarState: IKurobaToolbarState) {
    toolbarState as KurobaReplyToolbarState

    _toolbarMenu.value = toolbarState._toolbarMenu.value
  }

  override fun onPushed() {
  }

  override fun onPopped() {
  }
}