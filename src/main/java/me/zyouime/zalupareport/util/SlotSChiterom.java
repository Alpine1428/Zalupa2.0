package me.zyouime.zalupareport.util;

public record SlotSChiterom(String nickName, String detect) {
    @Override
    public boolean equals(Object obj) {
        return obj instanceof SlotSChiterom s && s.nickName.equals(this.nickName) && s.detect.equals(this.detect);
    }
}
