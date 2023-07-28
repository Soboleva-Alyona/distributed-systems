import java.util.*;

public class ConsistentHashImpl<K> implements ConsistentHash<K> {

    private int cntShards = 0;

    private static final int PLUS_INF = Integer.MAX_VALUE;

    private static final int MINUS_INF = Integer.MIN_VALUE;

    private TreeMap<Integer, Shard> vnodeToShard = new TreeMap<>();

    private HashMap<Shard, Set<Integer>> shardToVnodes = new HashMap<>();

    @Override
    public Shard getShardByKey(final K key) {
        final int hash = key.hashCode();
        return getShardByHash(hash);
    }

    @Override
    public Map<Shard, Set<HashRange>> addShard(final Shard newShard, final Set<Integer> vnodeHashes) {
        if (cntShards == 0) {
            putFirstShard(newShard, vnodeHashes);
            shardToVnodes.put(newShard, vnodeHashes);
            return new HashMap<>();
        }

        final List<Map.Entry<Shard, HashRange>> intervalsToGive = new ArrayList<>();
        getIntervalsToGiveToNewShard(vnodeHashes, intervalsToGive, Action.ADD);

        final Map<Shard, Set<HashRange>> shardToRangeSet = new HashMap<>();
        constructShardToSetRanges(intervalsToGive, shardToRangeSet);

        for (final var hash : vnodeHashes) {
            vnodeToShard.put(hash, newShard);
        }
        shardToVnodes.put(newShard, vnodeHashes);
        return shardToRangeSet;
    }


    @Override
    public Map<Shard, Set<HashRange>> removeShard(final Shard shard) {
        final var vnodesToRemove = shardToVnodes.get(shard);

        final List<Map.Entry<Shard, HashRange>> intervalsToGive = new ArrayList<>();
        getIntervalsToGiveToNewShard(vnodesToRemove, intervalsToGive, Action.REMOVE);

        final Map<Shard, Set<HashRange>> shardToRangeSet = new HashMap<>();
        constructShardToSetRanges(intervalsToGive, shardToRangeSet);

        for (final var vnode : vnodesToRemove) {
            vnodeToShard.remove(vnode);
        }
        shardToVnodes.remove(shard);

        return shardToRangeSet;
    }

    private void getIntervalsToGiveToNewShard(final Set<Integer> vnodeHashes, final List<Map.Entry<Shard, HashRange>> intervalsToGive, final Action action) {
        var prevInterval = new HashRange(MINUS_INF, PLUS_INF);
        for (final var hash : getVnodesInRingOrder(vnodeHashes)) {
            final var leftBorder = getLowerHash(hash) + 1;
            final var rightBorder = getHigherHash(hash);


            var newShardInterval = new HashRange(leftBorder, hash);
            final var otherShard = getShardByHash(leftBorder);
            if (action == Action.ADD && leftBorder == prevInterval.getLeftBorder()) {
                // join intervals (delete last added interval and replace it with a new one)
                intervalsToGive.remove(intervalsToGive.size() - 1);

                final var isPrevRangeLongerThenNewRange = isFirstLongerThenSecond(prevInterval, newShardInterval);
                if (newShardInterval.getRightBorder() > 0 && prevInterval.getRightBorder() < 0 || isPrevRangeLongerThenNewRange) {
                    newShardInterval = new HashRange(leftBorder, prevInterval.getRightBorder());
                }
            }

            if (action == Action.REMOVE && newShardInterval.getLeftBorder() == prevInterval.getRightBorder() + 1) {
                intervalsToGive.remove(intervalsToGive.size() - 1);
                newShardInterval = new HashRange(prevInterval.getLeftBorder(), newShardInterval.getRightBorder());
            }

            prevInterval = newShardInterval;

            if (action == Action.ADD) {
                intervalsToGive.add(Map.entry(otherShard, newShardInterval));
            } else {
                final var newShard = getShardByHash(rightBorder);
                intervalsToGive.add(Map.entry(newShard, newShardInterval));
            }
        }
        if (action == Action.REMOVE && intervalsToGive.size() > 1) {
            final var firstInTheRing = intervalsToGive.get(0);
            final var lastInTheRing = intervalsToGive.get(intervalsToGive.size() - 1);
            if (firstInTheRing.getValue().getLeftBorder() - 1 == lastInTheRing.getValue().getRightBorder()) {
                intervalsToGive.remove(intervalsToGive.size() - 1);
                intervalsToGive.remove(0);
                intervalsToGive.add(Map.entry(firstInTheRing.getKey(), new HashRange(lastInTheRing.getValue().getLeftBorder(), firstInTheRing.getValue().getRightBorder())));
            }
        }
        if (action == Action.ADD && intervalsToGive.size() > 1) {
            final var firstInTheRing = intervalsToGive.get(0);
            final var lastInTheRing = intervalsToGive.get(intervalsToGive.size() - 1);
            if (firstInTheRing.getValue().getLeftBorder() == lastInTheRing.getValue().getLeftBorder()) {
                intervalsToGive.remove(intervalsToGive.size() - 1);
                intervalsToGive.remove(0);
                intervalsToGive.add(Map.entry(firstInTheRing.getKey(), new HashRange(lastInTheRing.getValue().getLeftBorder(), firstInTheRing.getValue().getRightBorder())));
            }
        }
    }

    private boolean isFirstLongerThenSecond(HashRange range1, HashRange range2) {
        final var r1 = range1.getRightBorder() - range1.getLeftBorder();
        final var r2 = range2.getRightBorder() - range2.getLeftBorder();
        if (r1 > 0 && r2 > 0) {
            return r1 > r2;
        }
        if (r1 < 0 && r2 > 0) {
            return true;
        }
        if (r1 < 0 && r2 < 0) {
            return r1 > r2;
        }

        return false;
    }

    private List<Integer> getVnodesInRingOrder(final Set<Integer> vnodeHashes) {
        final var positives = new ArrayList<>(vnodeHashes.stream().filter(it -> it >= 0).sorted().toList());
        final var negatives = vnodeHashes.stream().filter(it -> it < 0).sorted().toList();
        positives.addAll(negatives);
        return positives;
    }

    private Shard getShardByHash(int hash) {
        if (vnodeToShard.get(hash) != null) {
            return vnodeToShard.get(hash);
        }

        var vnodeWithMyKey = vnodeToShard.higherKey(hash);
        if (vnodeWithMyKey == null) {
            vnodeWithMyKey = vnodeToShard.higherKey(MINUS_INF);
        }
        return vnodeToShard.get(vnodeWithMyKey);
    }

    private int getLowerHash(final int hash) {
        var vnodeWithMyKey = vnodeToShard.lowerKey(hash);
        if (vnodeWithMyKey == null) {
            vnodeWithMyKey = vnodeToShard.lowerKey(PLUS_INF);
        }
        return vnodeWithMyKey;
    }

    private int getHigherHash(final int hash) {
        var vnodeWithMyKey = vnodeToShard.higherKey(hash);
        if (vnodeWithMyKey == null) {
            vnodeWithMyKey = vnodeToShard.higherKey(MINUS_INF);
        }
        return vnodeWithMyKey;
    }

    private void putFirstShard(Shard newShard, Set<Integer> vnodeHashes) {
        for (final var vnode : vnodeHashes) {
            vnodeToShard.put(vnode, newShard);
        }
        cntShards++;
    }

    private void constructShardToSetRanges(List<Map.Entry<Shard, HashRange>> intervalsToGive, Map<Shard, Set<HashRange>> shardToRangeSet) {
        for (final var givenShardToInterval : intervalsToGive) {
            final var shard = givenShardToInterval.getKey();
            final var interval = givenShardToInterval.getValue();

            if (shardToRangeSet.get(shard) != null) {
                shardToRangeSet.get(shard).add(interval);
            } else {
                shardToRangeSet.put(shard, new HashSet<>(Collections.singletonList(interval)));
            }
        }
    }

    private enum Action {
        REMOVE, ADD
    }

}
