/*
 * Copyright (c) 2008-2018, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.spi.merge;

import com.hazelcast.nio.serialization.DataSerializable;
import com.hazelcast.spi.serialization.SerializationService;

/**
 * Represents a read-only view of a data structure value for the merging process after a split-brain.
 *
 * @param <V> the type of the value
 * @since 3.10
 */
public interface MergingValueHolder<V> extends DataSerializable {

    /**
     * Returns the merging value in the in-memory format of the backing data structure.
     *
     * @return the merging value
     */
    V getValue();

    /**
     * Returns the deserialized merging value.
     *
     * @return the deserialized merging value
     */
    Object getDeserializedValue();

    /**
     * Sets the {@link SerializationService} to deserialize the value on demand.
     *
     * @param serializationService the serialization service
     */
    void setSerializationService(SerializationService serializationService);
}
