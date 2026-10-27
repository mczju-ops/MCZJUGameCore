package com.github.mczjuops.mczjugamecore.game.kit.seat;

import com.github.mczjuops.mczjugamecore.game.kit.support.FakeSeat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link SeatOrder}：座次、下家、座次距离。
 *
 * <p>日麻靠 {@link SeatOrder#distance} 排荣和优先级（头跳），错了就是给错人付钱。
 */
class SeatOrderTest {

    private final FakeSeat east = new FakeSeat("起家", true);
    private final FakeSeat south = new FakeSeat("南家", true);
    private final FakeSeat west = new FakeSeat("西家", true);
    private final FakeSeat north = new FakeSeat("北家", true);
    private final SeatOrder<FakeSeat> order = new SeatOrder<>(List.of(east, south, west, north));

    @Test
    void nextWrapsAround() {
        assertEquals(south, order.next(east));
        assertEquals(west, order.next(south));
        assertEquals(north, order.next(west));
        assertEquals(east, order.next(north), "北家的下家回到起家");
    }

    @Test
    void previousWrapsAround() {
        assertEquals(north, order.previous(east), "起家的上家是北家");
        assertEquals(east, order.previous(south));
    }

    @Test
    void distanceStartsAtZeroForSelf() {
        assertEquals(0, order.distance(east, east));
        assertEquals(1, order.distance(east, south));
        assertEquals(3, order.distance(east, north));
        assertEquals(1, order.distance(north, east), "距离要按环形算，不能是 -3");
    }

    @Test
    void afterExcludesSelfAndKeepsOrder() {
        assertEquals(List.of(south, west, north), order.after(east));
    }

    @Test
    void atIsModular() {
        assertEquals(east, order.at(0));
        assertEquals(east, order.at(4));
        assertEquals(north, order.at(-1));
    }

    @Test
    void unknownSeatIsRejected() {
        FakeSeat stranger = new FakeSeat("路人", true);
        assertThrows(IllegalArgumentException.class, () -> order.next(stranger));
        assertEquals(-1, order.indexOf(stranger));
    }
}
