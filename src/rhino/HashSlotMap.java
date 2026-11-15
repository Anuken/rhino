/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino;

import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * This class implements the SlotMap interface using a java.util.HashMap. This class has more
 * overhead than EmbeddedSlotMap, especially because it puts each "Slot" inside an intermediate
 * object. However it is much more resistant to large number of hash collisions than EmbeddedSlotMap
 * and therefore we use this implementation when an object gains a large number of properties.
 */
public class HashSlotMap<T extends PropHolder<T>> implements SlotMap<T> {

    private final LinkedHashMap<Object, Slot<T>> map;

    public HashSlotMap() {
        map = new LinkedHashMap<>();
    }

    protected HashSlotMap(int capacity) {
        map = new LinkedHashMap<>(capacity);
    }

    public HashSlotMap(SlotMap<T> oldMap) {
        map = new LinkedHashMap<>(oldMap.size());
        for (var n : oldMap) {
            add(null, n.copySlot());
        }
    }

    public HashSlotMap(SlotMap<T> oldMap, Slot<T> newSlot) {
        map = new LinkedHashMap<>(oldMap.dirtySize() + 1);
        for (var n : oldMap) {
            add(null, n.copySlot());
        }
        add(null, newSlot);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public Slot<T> query(Object key, int index) {
        Object name = makeKey(key, index);
        return map.get(name);
    }

    @Override
    public Slot<T> modify(SlotMapOwner<T> owner, Object key, int index, int attributes) {
        Object name = makeKey(key, index);
        Slot<T> slot = map.get(name);
        if (slot == null) {
            slot = new StandardSlot<T>(key, index, attributes);
            map.put(name, slot);
        }
        return slot;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <S extends Slot<T>> S compute(
            SlotMapOwner<T> owner,
            CompoundOperationMap<T> compoundOp,
            Object key,
            int index,
            SlotComputer<S, T> c) {
        Object name = makeKey(key, index);
        Slot<T> existing = map.get(name);
        S ret = c.compute(key, index, existing, compoundOp, owner);
        if (ret != null) {
            map.put(name, ret);
        } else if (existing != null) {
            map.remove(name);
        }
        return ret;
    }

    @Override
    public void add(SlotMapOwner<T> owner, Slot<T> newSlot) {
        Object name = makeKey(newSlot);
        map.put(name, newSlot);
    }

    @Override
    public Iterator<Slot<T>> iterator() {
        return map.values().iterator();
    }

    private Object makeKey(Object name, int index) {
        return name == null ? index : name;
    }

    private Object makeKey(Slot<T> slot) {
        return slot.getKey();
    }

    @Override
    public SlotMap<T> copyMap() {
        return new HashSlotMap<>(this);
    }
}
