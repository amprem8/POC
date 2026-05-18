package com.example.poc

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation

@Composable
fun rememberPassKeyInputController(): PassKeyInputController {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    return remember(focusManager, keyboardController) {
        PassKeyInputController(
            focusManager = focusManager,
            keyboardController = keyboardController,
        )
    }
}

class PassKeyInputController internal constructor(
    private val focusManager: FocusManager,
    private val keyboardController: SoftwareKeyboardController?,
) {
    fun dismiss() {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }

    fun keyboardActions(
        nextFocusRequester: FocusRequester? = null,
        onSubmit: (() -> Unit)? = null,
    ): KeyboardActions {
        return KeyboardActions(
            onNext = {
                if (nextFocusRequester != null) {
                    nextFocusRequester.requestFocus()
                } else {
                    focusManager.moveFocus(FocusDirection.Down)
                }
            },
            onDone = {
                dismiss()
                onSubmit?.invoke()
            },
        )
    }
}

fun Modifier.dismissKeyboardOnTapOutside(inputController: PassKeyInputController): Modifier = composed {
    pointerInput(inputController) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val up = waitForUpOrCancellation()

            if (up != null && !up.isConsumed) {
                inputController.dismiss()
            }
        }
    }
}



