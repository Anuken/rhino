package rhino;

import java.util.concurrent.locks.ReentrantReadWriteLock;

class ThreadSafeEmbeddedSlotMap<T extends PropHolder<T>> extends EmbeddedSlotMap<T>
        implements LockAwareSlotMap<T> {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile LockAwareSlotMap<T> current = this;

    public ThreadSafeEmbeddedSlotMap() {
        super();
    }

    public ThreadSafeEmbeddedSlotMap(int capacity) {
        super(capacity);
    }

    protected ThreadSafeEmbeddedSlotMap(ThreadSafeEmbeddedSlotMap<T> other) {
        super(other);
    }

    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return current.sizeWithLock();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int dirtySize() {
        assert lock.getReadLockCount() > 0 || lock.isWriteLocked();
        return current.sizeWithLock();
    }

    @Override
    public boolean isEmpty() {
        lock.readLock().lock();
        try {
            return current.isEmptyWithLock();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Slot<T> modify(SlotMapOwner<T> owner, Object key, int index, int attributes) {
        lock.writeLock().lock();
        try {
            return current.modifyWithLock(owner, key, index, attributes);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public <S extends Slot<T>> S compute(
            SlotMapOwner<T> owner,
            CompoundOperationMap<T> mutableMap,
            Object key,
            int index,
            SlotComputer<S, T> c) {
        return current.computeWithLock(owner, mutableMap, key, index, c);
    }

    @Override
    public Slot<T> query(Object key, int index) {
        lock.readLock().lock();
        try {
            return current.queryWithLock(key, index);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void add(SlotMapOwner<T> owner, Slot<T> newSlot) {
        lock.writeLock().lock();
        try {
            current.addWithLock(owner, newSlot);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void addWithLock(SlotMapOwner<T> owner, Slot<T> newSlot) {
        super.add(owner, newSlot);
    }

    @Override
    public <S extends Slot<T>> S computeWithLock(
            SlotMapOwner<T> owner,
            CompoundOperationMap<T> mutableMap,
            Object key,
            int index,
            SlotComputer<S, T> compute) {
        return super.compute(owner, mutableMap, key, index, compute);
    }

    @Override
    public boolean isEmptyWithLock() {
        return super.isEmpty();
    }

    @Override
    public Slot<T> modifyWithLock(SlotMapOwner<T> owner, Object key, int index, int attributes) {
        return super.modify(owner, key, index, attributes);
    }

    @Override
    public Slot<T> queryWithLock(Object key, int index) {
        return super.query(key, index);
    }

    @Override
    public int sizeWithLock() {
        return super.size();
    }

    @Override
    public long getReadLock() {
        lock.readLock().lock();
        return READ_STAMP;
    }

    @Override
    public long getWriteLock() {
        lock.writeLock().lock();
        return WRITE_STAMP;
    }

    @Override
    public void releaseLock(long stamp) {
        if (stamp == WRITE_STAMP) {
            lock.writeLock().unlock();
        } else {
            lock.readLock().unlock();
        }
    }

    @Override
    protected void promoteMap(SlotMapOwner<T> owner, Slot<T> newSlot) {
        // We can use `setMap` here as this promotion can only be done
        // by the lock holder and the operation will be being done on
        // the "current" map.
        var newMap = new ThreadSafeHashSlotMap<>(lock, this, newSlot);
        owner.setMap(newMap);
        current = newMap;
    }

    @Override
    public SlotMap<T> copyMap() {
        lock.readLock().lock();
        try {
            return new ThreadSafeEmbeddedSlotMap<>(this);
        } finally {
            lock.readLock().unlock();
        }
    }
}
