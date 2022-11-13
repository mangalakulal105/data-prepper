/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.aggregate;

import com.google.common.collect.Maps;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class AggregateGroupManager {

    private final Map<AggregateIdentificationKeysHasher.IdentificationHash, AggregateGroup> allGroups = Maps.newConcurrentMap();
    private final Duration groupDuration;

    AggregateGroupManager(final Duration groupDuration) {
        this.groupDuration = groupDuration;
    }

    AggregateGroup getAggregateGroup(final AggregateIdentificationKeysHasher.IdentificationHash identificationHash) {
        return allGroups.computeIfAbsent(identificationHash, (hash) -> new AggregateGroup());
    }

    List<Map.Entry<AggregateIdentificationKeysHasher.IdentificationHash, AggregateGroup>> getGroupsToConclude(final boolean forceConclude) {
        final List<Map.Entry<AggregateIdentificationKeysHasher.IdentificationHash, AggregateGroup>> groupsToConclude = new ArrayList<>();
        for (final Map.Entry<AggregateIdentificationKeysHasher.IdentificationHash, AggregateGroup> groupEntry : allGroups.entrySet()) {
            if (groupEntry.getValue().shouldConcludeGroup(groupDuration) || forceConclude) {
                groupsToConclude.add(groupEntry);
            }
        }
        return groupsToConclude;
    }

    void closeGroup(final AggregateIdentificationKeysHasher.IdentificationHash hash, final AggregateGroup group) {
        allGroups.remove(hash, group);
        group.resetGroup();
    }

    void putGroupWithHash(final AggregateIdentificationKeysHasher.IdentificationHash hash, final AggregateGroup group) {
        allGroups.put(hash, group);
    }

    long getAllGroupsSize() {
        return allGroups.size();
    }

    Duration getGroupDuration() {
        return this.groupDuration;
    }
}
