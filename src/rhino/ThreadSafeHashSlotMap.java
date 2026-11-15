package rhino;

import java.util.concurrent.locks.ReentrantReadWriteLock;

class ThreadSafeHashSlotMap<T extends PropHolder<T>> extends HashSlotMap<T>
        implements LockAwareSlotMap<T> {

    private final ReentrantReadWriteLock lock;

    public ThreadSafeHashSlotMap() {
        super();
        lock = new ReentrantReadWriteLock();
    }

    public ThreadSafeHashSlotMap(int capacity) {
        super(capacity);
        lock = new ReentrantReadWriteLock();
    }

    public ThreadSafeHashSlotMap(ReentrantReadWriteLock lock, SlotMap<T> oldMap) {
        super(oldMap.dirtySize());
        this.lock = lock;
        for (var n : oldMap) {
            addWithLock(null, n.copySlot());
        }
    }

    public ThreadSafeHashSlotMap(ReentrantReadWriteLock lock, SlotMap<T> oldMap, Slot<T> newSlot) {
        super(oldMap.dirtySize() + 1);
        this.lock = lock;
        for (var n : oldMap) {
            addWithLock(null, n.copySlot());
        }
        addWithLock(null, newSlot);
    }

    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return super.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int dirtySize() {
        assert lock.getReadLockCount() > 0 || lock.isWriteLocked();
        return super.size();
    }

    @Override
    public boolean isEmpty() {
        lock.readLock().lock();
        try {
            return super.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Slot<T> modify(SlotMapOwner<T> owner, Object key, int index, int attributes) {
        lock.writeLock().lock();
        try {
            return super.modify(owner, key, index, attributes);
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
        lock.writeLock().lock();
        try {
            return super.compute(owner, mutableMap, key, index, c);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Slot<T> query(Object key, int index) {
        lock.readLock().lock();
        try {
            return super.query(key, index);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void add(SlotMapOwner<T> owner, Slot<T> newSlot) {
        lock.writeLock().lock();
        try {
            super.add(owner, newSlot);
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
    public SlotMap<T> copyMap() {
        return new ThreadSafeHashSlotMap<>(new ReentrantReadWriteLock(), this);
    }
}
