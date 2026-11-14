package ke.writ;

import org.junit.jupiter.api.Test;

import static ke.writ.HeroGifts.GiftAction.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HeroGiftsTest {
    @Test void defaultDistribution() {
        for (int roll = 0; roll < 100; roll++)
            assertEquals(roll < 2 ? CHAOS : roll < 10 ? RANDOM : ORIGINAL,
                    HeroGifts.decide(roll, 8, 2, true, true), "roll " + roll);
    }

    @Test void percentageBoundaries() {
        for (int roll = 0; roll < 100; roll++) {
            assertEquals(RANDOM, HeroGifts.decide(roll, 100, 0, true, true));
            assertEquals(CHAOS, HeroGifts.decide(roll, 0, 100, true, true));
            assertEquals(roll < 50 ? CHAOS : RANDOM, HeroGifts.decide(roll, 50, 50, true, true));
            assertEquals(ORIGINAL, HeroGifts.decide(roll, 0, 0, true, true));
        }
    }

    @Test void unavailableBranchesKeepOriginal() {
        for (int roll = 0; roll < 100; roll++) {
            assertEquals(roll >= 2 && roll < 10 ? RANDOM : ORIGINAL,
                    HeroGifts.decide(roll, 8, 2, false, true));
            assertEquals(roll < 2 ? CHAOS : ORIGINAL,
                    HeroGifts.decide(roll, 8, 2, true, false));
            assertEquals(ORIGINAL, HeroGifts.decide(roll, 8, 2, false, false));
        }
    }

    @Test void invalidArgumentsKeepOriginal() {
        assertEquals(ORIGINAL, HeroGifts.decide(0, 3, 98, true, true));
        assertEquals(ORIGINAL, HeroGifts.decide(0, -1, 2, true, true));
        assertEquals(ORIGINAL, HeroGifts.decide(0, 8, -1, true, true));
        assertEquals(ORIGINAL, HeroGifts.decide(-1, 8, 2, true, true));
        assertEquals(ORIGINAL, HeroGifts.decide(100, 8, 2, true, true));
        assertEquals(ORIGINAL, HeroGifts.decide(0, Integer.MAX_VALUE, Integer.MAX_VALUE, true, true));
    }
}
