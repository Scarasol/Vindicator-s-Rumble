package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.helicopter;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HelicopterRecoveryCandidateTest {
    private static final Vec3 HEADING = new Vec3(1.0D, 0.0D, 0.0D);

    @Test
    void rootIsNotAWaypoint() {
        HelicopterRecoveryCandidate root = node(new Vec3(12.0D, 18.0D, -4.0D), null);

        assertEquals(List.of(), root.path(Integer.MAX_VALUE));
        assertEquals(waypointValue(root, List.of()).hashCode(), root.hashCode());
    }

    @Test
    void reconstructsWaypointsInFlightOrder() {
        HelicopterRecoveryCandidate root = node(Vec3.ZERO, null);
        HelicopterRecoveryCandidate first = node(new Vec3(5.0D, 20.0D, 1.0D), root);
        HelicopterRecoveryCandidate second = node(new Vec3(10.0D, 23.0D, 2.0D), first);
        HelicopterRecoveryCandidate third = node(new Vec3(15.0D, 25.0D, 3.0D), second);
        List<Vec3> expected = List.of(first.position(), second.position(), third.position());

        assertEquals(expected, third.path(Integer.MAX_VALUE));
        assertEquals(waypointValue(third, expected).hashCode(), third.hashCode());
        assertThrows(UnsupportedOperationException.class, () -> third.path(3).add(Vec3.ZERO));
    }

    @Test
    void safePrefixUsesTheFirstWaypointsNotTheLast() {
        HelicopterRecoveryCandidate first = node(new Vec3(1.0D, 20.0D, 0.0D), node(Vec3.ZERO, null));
        HelicopterRecoveryCandidate second = node(new Vec3(2.0D, 20.0D, 0.0D), first);
        HelicopterRecoveryCandidate third = node(new Vec3(3.0D, 20.0D, 0.0D), second);

        assertEquals(List.of(first.position(), second.position()), third.path(2));
        assertEquals(List.of(first.position()), third.path(1));
        assertEquals(List.of(), third.path(0));
        assertEquals(List.of(first.position()), first.path(2));
    }

    @Test
    void siblingRoutesDoNotModifySharedWaypoints() {
        HelicopterRecoveryCandidate first = node(new Vec3(1.0D, 20.0D, 0.0D), node(Vec3.ZERO, null));
        HelicopterRecoveryCandidate left = node(new Vec3(2.0D, 20.0D, -1.0D), first);
        HelicopterRecoveryCandidate right = node(new Vec3(2.0D, 20.0D, 1.0D), first);

        assertEquals(List.of(first.position(), left.position()), left.path(2));
        assertEquals(List.of(first.position(), right.position()), right.path(2));
        assertEquals(List.of(first.position()), first.path(2));
    }

    @Test
    void equalPathsIgnoreRootAndAncestorMetadata() {
        HelicopterRecoveryCandidate firstRoot = node(Vec3.ZERO, null);
        HelicopterRecoveryCandidate secondRoot = node(new Vec3(-9.0D, 40.0D, 2.0D), null);
        Vec3 waypoint = new Vec3(1.0D, 20.0D, 0.0D);
        HelicopterRecoveryCandidate first = node(waypoint, firstRoot);
        HelicopterRecoveryCandidate second = new HelicopterRecoveryCandidate(
                waypoint, new Vec3(0.0D, 0.0D, 1.0D), secondRoot, -1, true, 17.0D, 42.0D);
        HelicopterRecoveryCandidate left = node(new Vec3(2.0D, 20.0D, 0.0D), first);
        HelicopterRecoveryCandidate right = node(left.position(), second);

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        LinkedHashSet<HelicopterRecoveryCandidate> retained = new LinkedHashSet<>();
        retained.add(left);
        retained.add(right);
        assertEquals(1, retained.size());
        assertSame(left, retained.iterator().next());
    }

    @Test
    void differentIntermediateWaypointsOrLengthsRemainDistinct() {
        HelicopterRecoveryCandidate root = node(Vec3.ZERO, null);
        HelicopterRecoveryCandidate leftParent = node(new Vec3(1.0D, 20.0D, -1.0D), root);
        HelicopterRecoveryCandidate rightParent = node(new Vec3(1.0D, 20.0D, 1.0D), root);
        HelicopterRecoveryCandidate left = node(new Vec3(2.0D, 20.0D, 0.0D), leftParent);
        HelicopterRecoveryCandidate right = node(left.position(), rightParent);

        assertNotEquals(left, right);
        assertNotEquals(left, node(left.position(), root));
    }

    @Test
    void endpointMetadataRemainsPartOfCandidateIdentity() {
        HelicopterRecoveryCandidate root = node(Vec3.ZERO, null);
        HelicopterRecoveryCandidate candidate = node(new Vec3(1.0D, 20.0D, 0.0D), root);

        assertNotEquals(candidate, new HelicopterRecoveryCandidate(
                candidate.position(), new Vec3(0.0D, 0.0D, 1.0D), root, 0, false, 1.0D, 2.0D));
        assertNotEquals(candidate, new HelicopterRecoveryCandidate(
                candidate.position(), HEADING, root, -1, false, 1.0D, 2.0D));
        assertNotEquals(candidate, new HelicopterRecoveryCandidate(
                candidate.position(), HEADING, root, 0, true, 1.0D, 2.0D));
        assertNotEquals(candidate, new HelicopterRecoveryCandidate(
                candidate.position(), HEADING, root, 0, false, 3.0D, 2.0D));
        assertNotEquals(candidate, new HelicopterRecoveryCandidate(
                candidate.position(), HEADING, root, 0, false, 1.0D, 4.0D));
    }

    @Test
    void floatingPointIdentityKeepsRecordSemantics() {
        HelicopterRecoveryCandidate root = node(Vec3.ZERO, null);
        HelicopterRecoveryCandidate positiveZero = new HelicopterRecoveryCandidate(
                HEADING, HEADING, root, 0, false, 0.0D, Double.NaN);
        HelicopterRecoveryCandidate negativeZero = new HelicopterRecoveryCandidate(
                HEADING, HEADING, root, 0, false, -0.0D, Double.NaN);
        HelicopterRecoveryCandidate anotherNaN = new HelicopterRecoveryCandidate(
                HEADING, HEADING, root, 0, false, 0.0D, Double.longBitsToDouble(0x7ff8000000000001L));

        assertNotEquals(positiveZero, negativeZero);
        assertEquals(positiveZero, anotherNaN);
        assertEquals(positiveZero.hashCode(), anotherNaN.hashCode());
        assertEquals(waypointValue(positiveZero, List.of(HEADING)).hashCode(), positiveZero.hashCode());
    }

    private static HelicopterRecoveryCandidate node(Vec3 position, HelicopterRecoveryCandidate parent) {
        return new HelicopterRecoveryCandidate(position, HEADING, parent, 0, false, 1.0D, 2.0D);
    }

    private static WaypointValue waypointValue(HelicopterRecoveryCandidate candidate, List<Vec3> path) {
        return new WaypointValue(candidate.position(), candidate.heading(), path,
                candidate.lane(), candidate.climbed(), candidate.travelCost(), candidate.score());
    }

    private record WaypointValue(Vec3 position, Vec3 heading, List<Vec3> path,
                                 int lane, boolean climbed, double travelCost, double score) {
    }
}
