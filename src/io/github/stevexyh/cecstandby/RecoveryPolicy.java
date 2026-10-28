package io.github.stevexyh.cecstandby;

/** Pure policy shared by runtime and offline tests; never repeatedly toggle a closed port. */
final class RecoveryPolicy {
    private long lastAttempt=Long.MIN_VALUE/2;
    boolean shouldRestore(boolean optedIn,boolean disabled,long now) {
        if(!optedIn||!disabled||now-lastAttempt<60_000)return false;
        lastAttempt=now;return true;
    }
}
