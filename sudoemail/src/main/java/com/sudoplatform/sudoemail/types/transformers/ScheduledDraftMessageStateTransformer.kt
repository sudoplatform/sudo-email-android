package com.sudoplatform.sudoemail.types.transformers

import com.sudoplatform.sudoemail.types.ScheduledDraftMessageState
import com.sudoplatform.sudoemail.graphql.type.ScheduledDraftMessageState as ScheduledDraftMessageStateGraphQl

object ScheduledDraftMessageStateTransformer {

    fun toEntity(
        graphQl: ScheduledDraftMessageStateGraphQl,
    ): ScheduledDraftMessageState {
        for (value in ScheduledDraftMessageState.entries) {
            if (value.name == graphQl.name) {
                return value
            }
        }
        return ScheduledDraftMessageState.UNKNOWN
    }

    fun toGraphQl(
        entity: ScheduledDraftMessageState,
    ): ScheduledDraftMessageStateGraphQl {
        for (value in ScheduledDraftMessageStateGraphQl.entries) {
            if (value.name == entity.name) {
                return value
            }
        }
        return ScheduledDraftMessageStateGraphQl.UNKNOWN__
    }
}
