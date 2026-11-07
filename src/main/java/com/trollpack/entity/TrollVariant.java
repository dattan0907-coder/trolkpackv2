package com.trollpack.entity;

import net.minecraft.util.RandomSource;

/** Random look of a plain troll, packed into one int so it can be synced + saved. */
public record TrollVariant(int skin, boolean fem, boolean armorL, boolean armorR, boolean shield, boolean spear,
                           int head, boolean tusk1, boolean tusk2) {

    public static final int HEADS = 12;

    public static TrollVariant random(RandomSource r) {
        return new TrollVariant(r.nextInt(3), r.nextInt(4) == 0, r.nextInt(6) == 0, r.nextInt(6) == 0,
                r.nextInt(3) == 0, r.nextInt(3) == 0, 1 + r.nextInt(HEADS), r.nextInt(5) == 0, r.nextInt(5) == 0);
    }

    public int pack() {
        int v = skin & 3;
        v |= (fem ? 1 : 0) << 2;
        v |= (armorL ? 1 : 0) << 3;
        v |= (armorR ? 1 : 0) << 4;
        v |= (shield ? 1 : 0) << 5;
        v |= (spear ? 1 : 0) << 6;
        v |= (tusk1 ? 1 : 0) << 7;
        v |= (tusk2 ? 1 : 0) << 8;
        v |= (head & 31) << 9;
        return v;
    }

    public static TrollVariant unpack(int v) {
        int h = (v >> 9) & 31;
        return new TrollVariant(Math.min(v & 3, 2), (v >> 2 & 1) == 1, (v >> 3 & 1) == 1, (v >> 4 & 1) == 1,
                (v >> 5 & 1) == 1, (v >> 6 & 1) == 1, h < 1 || h > HEADS ? 1 : h, (v >> 7 & 1) == 1, (v >> 8 & 1) == 1);
    }
}
