package com.sudoplatform.sudoemail.util

import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.types.inputs.CancelScheduledDraftMessageInput
import com.sudoplatform.sudologging.Logger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Helper class to allow invocation of suspendable SudoEmailClient methods without waiting
 * for them to complete.
 */
internal class EmailClientInvoker(private val emailClient: SudoEmailClient, private val logger: Logger) {
    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        this.logger.info("Exception occurred during async invocation")
    }

    private val customScope = CoroutineScope(
        Dispatchers.IO +
            SupervisorJob() +
            exceptionHandler,
    )

    fun cancelScheduledDraftEmailMessage(emailMessageId: String, emailAddressId: String) {
        customScope.launch {
            try {
                // Background work
                emailClient.cancelScheduledDraftMessage(CancelScheduledDraftMessageInput(emailMessageId, emailAddressId))
            } catch (c: CancellationException) {
                throw c
            } catch (e: Throwable) {
                this@EmailClientInvoker.logger.info("Failed to cancel scheduled draft $emailMessageId")
            }
        }
    }
}
