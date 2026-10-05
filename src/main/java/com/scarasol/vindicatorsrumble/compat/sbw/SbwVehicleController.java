package com.scarasol.vindicatorsrumble.compat.sbw;

import com.atsuishio.superbwarfare.data.StringOrVec3;
import com.atsuishio.superbwarfare.data.gun.FireMode;
import com.atsuishio.superbwarfare.data.gun.FireModeInfo;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.gun.GunType;
import com.atsuishio.superbwarfare.data.gun.ProjectileInfo;
import com.atsuishio.superbwarfare.data.gun.SeekWeaponInfo;
import com.atsuishio.superbwarfare.data.gun.SeekType;
import com.atsuishio.superbwarfare.data.vehicle.subdata.EngineType;
import com.atsuishio.superbwarfare.data.vehicle.subdata.EngineInfo;
import com.atsuishio.superbwarfare.data.vehicle.subdata.SeatInfo;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.tools.RangeTool;
import com.scarasol.vindicatorsrumble.api.vehicle.SeatHandle;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleDimensions;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleFlightInput;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleInput;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleMovementType;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponAimMode;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponFireWindow;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponFireMode;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponHandle;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponPayloadType;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponStatus;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.vindicatorsrumble.util.VehicleAiLogger;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class SbwVehicleController implements VehicleController {
    private static final String EMPTY_AI_TARGET = "undefined";
    private static final double INDEPENDENT_FIRE_ALIGNMENT_DEGREES = 4.0D;
    private static final double RIDER_AIMED_FIRE_ALIGNMENT_DEGREES = 4.0D;
    private static final double FIXED_FIRE_ALIGNMENT_DEGREES = 7.0D;
    private static final double MIN_AIM_VECTOR_SQR = 1.0E-6D;
    private static final double FLIGHT_ROLL_COUNTER_RATIO = 1.0D;
    private static final double HOVER_RESIDUAL_LIFT_SCALE = 0.5D;
    private static final float AIRBORNE_HOVER_POWER = 0.09F;
    private static final short SBW_LEFT_KEY = 0b000000001;
    private static final short SBW_RIGHT_KEY = 0b000000010;

    private final VehicleEntity vehicle;

    public SbwVehicleController(VehicleEntity vehicle) {
        this.vehicle = vehicle;
    }

    @Override
    public Entity entity() {
        return this.vehicle;
    }

    @Override
    public Level level() {
        return this.vehicle.level();
    }

    @Override
    public AABB boundingBox() {
        return this.vehicle.getBoundingBox();
    }

    @Override
    public double horizontalFootprintRadius() {
        var collision = this.vehicle.getCollisionOBBInfo();
        return collision == null ? VehicleController.super.horizontalFootprintRadius()
                : Math.hypot(collision.getSize().x, collision.getSize().z)
                + Math.hypot(collision.getPosition().x, collision.getPosition().z);
    }

    @Override
    public boolean isValid() {
        return !this.vehicle.isRemoved() && this.vehicle.isAlive();
    }

    @Override
    public boolean isWreck() {
        return this.vehicle.isWreck();
    }

    @Override
    public boolean isControllable() {
        return this.isValid() && !this.isWreck() && !this.seats().isEmpty();
    }

    @Override
    public Vec3 position() {
        return this.vehicle.position();
    }

    @Override
    public float yaw() {
        return this.vehicle.getYRot();
    }

    @Override
    public Vec3 velocity() {
        return this.vehicle.getDeltaMovement();
    }

    @Override
    public double estimatedHorizontalStoppingDistance() {
        return this.estimatedHorizontalStoppingDistance(1.0D);
    }

    @Override
    public double estimatedStabilizedHorizontalStoppingDistance() {
        return this.estimatedHorizontalStoppingDistance(HOVER_RESIDUAL_LIFT_SCALE);
    }

    @Override
    public double estimatedGroundTurningRadius() {
        if (this.vehicle.computed().getEngineType() != EngineType.WHEEL
                || !(this.vehicle.getEngineInfo() instanceof EngineInfo.Wheel engine)) {
            return 0.0D;
        }
        // SBW wheelEngine: full-lock deltaRot/rudder equilibrium after ground drag.
        double retention = Math.max(0.1D, 0.78D - 0.25D * 0.8295D * this.velocity().horizontalDistance());
        double rudder = Math.min(0.6D, 3.0D * engine.getSteeringSpeed() * 1.2D * retention / (1.0D - retention));
        return 1.0D / Math.toRadians(12.0D * 0.8295D * Math.max(1.0E-4D, rudder));
    }

    private double estimatedHorizontalStoppingDistance(double residualLiftScale) {
        Vec3 movement = this.vehicle.getDeltaMovement();
        double horizontalSpeed = Math.hypot(movement.x, movement.z);
        if (horizontalSpeed < 1.0E-4D) {
            return 0.0D;
        }
        if (this.vehicle.onGround()) {
            return horizontalSpeed / (this.vehicle.computed().getEngineType() == EngineType.HELICOPTER
                    ? 0.15D : 0.2D);
        }
        if (!(this.vehicle.getEngineInfo() instanceof EngineInfo.Helicopter engine)) {
            return 0.0D;
        }
        double alignment = movement.lengthSqr() < 1.0E-8D ? 0.0D
                : Math.abs(movement.normalize().dot(this.vehicle.getViewVector(1.0F)));
        double drag = Mth.clamp(0.93499D - 0.01D * movement.lengthSqr()
                + 0.07D * engine.getSpeed() + 0.001D * alignment, 0.01D, 0.99D);
        double hoverRetention = drag * 0.95D;
        Vec3 up = this.vehicle.getUpVec(1.0F);
        double rotor = this.vehicle.getSynchedPropellerRot();
        double horizontalLift = Math.hypot(up.x, up.z) * rotor * engine.getLiftSpeed() * 0.66D;
        double attitudeResponse = rotor * Math.min(engine.getPitchSpeed(), engine.getRollSpeed());
        double residualImpulse = residualLiftScale * horizontalLift / Math.max(0.02D, attitudeResponse);
        return (horizontalSpeed + residualImpulse)
                / Math.max(1.0E-4D, 1.0D - hoverRetention);
    }

    @Override
    public float pitch() {
        return this.vehicle.getXRot();
    }

    @Override
    public float roll() {
        return this.vehicle.getRoll();
    }

    @Override
    public VehicleMovementType movementType() {
        EngineType type = this.vehicle.computed().getEngineType();
        if (type == null) {
            return VehicleMovementType.UNKNOWN;
        }
        return switch (type) {
            case WHEEL, WHEELCHAIR, TOM6 -> VehicleMovementType.WHEEL;
            case TRACK -> VehicleMovementType.TRACK;
            case SHIP -> VehicleMovementType.SHIP;
            case AIRCRAFT -> VehicleMovementType.AIRCRAFT;
            case HELICOPTER -> VehicleMovementType.HELICOPTER;
            case FIXED -> VehicleMovementType.FIXED;
            default -> VehicleMovementType.UNKNOWN;
        };
    }

    @Override
    public boolean supportsFlightControl() {
        return this.movementType() == VehicleMovementType.HELICOPTER;
    }

    @Override
    public boolean hoverMode() {
        return this.vehicle.getHoverMode();
    }

    @Override
    public boolean engineStarted() {
        return this.vehicle.getEngineStartOver();
    }

    @Override
    public float power() {
        return this.vehicle.getPower();
    }

    @Override
    public VehicleDimensions pathingDimensions() {
        AABB box = this.vehicle.getBoundingBox();
        return new VehicleDimensions(
                Math.max(1.0D, box.getXsize()),
                Math.max(1.0D, box.getYsize()),
                Math.max(1.0D, box.getZsize())
        );
    }

    @Override
    public List<SeatHandle> seats() {
        int count = this.vehicle.getMaxPassengers();
        List<SeatHandle> seats = new ArrayList<>(count);
        VehicleMovementType movementType = this.movementType();
        boolean canDriveVehicle = movementType == VehicleMovementType.WHEEL
                || movementType == VehicleMovementType.TRACK
                || movementType == VehicleMovementType.SHIP
                || movementType == VehicleMovementType.HELICOPTER;
        for (int i = 0; i < count; i++) {
            Entity occupant = this.vehicle.getNthEntity(i);
            boolean canDrive = i == 0 && canDriveVehicle;
            seats.add(new SeatHandle(i, canDrive, this.hasMountedWeapon(i), Optional.ofNullable(occupant)));
        }
        return seats;
    }

    @Override
    public Optional<SeatHandle> seatOf(Mob mob) {
        int index = this.vehicle.getSeatIndex(mob);
        return this.seats().stream().filter(seat -> seat.id() == index).findFirst();
    }

    @Override
    public Optional<Entity> controllingPassenger() {
        return Optional.ofNullable(this.vehicle.getFirstPassenger());
    }

    @Override
    public List<Entity> passengers() {
        return this.vehicle.getPassengers();
    }

    @Override
    public boolean canMount(Mob mob) {
        return this.isControllable() && this.vehicle.getPassengers().size() < this.vehicle.getMaxPassengers();
    }

    @Override
    public boolean mount(Mob mob) {
        if (mob.getVehicle() == this.vehicle) {
            return true;
        }
        return this.canMount(mob) && mob.startRiding(this.vehicle);
    }

    @Override
    public boolean moveToSeat(Mob mob, SeatHandle seat) {
        if (mob.getVehicle() != this.vehicle) {
            return false;
        }
        int currentSeat = this.vehicle.getSeatIndex(mob);
        if (currentSeat == seat.id()) {
            return true;
        }
        return this.vehicle.changeSeat(mob, seat.id());
    }

    @Override
    public boolean dismount(Mob mob) {
        if (mob.getVehicle() != this.vehicle) {
            return true;
        }
        mob.stopRiding();
        return mob.getVehicle() != this.vehicle;
    }

    @Override
    public boolean canDrive(Mob mob) {
        int seatIndex = this.vehicle.getSeatIndex(mob);
        boolean canDrive = this.seatOf(mob).map(SeatHandle::canDrive).orElse(false);
        VehicleAiLogger.debugEvery(mob, 40, "SBW canDrive={} vehicle={} seat={} movement={} firstPassenger={}",
                canDrive,
                this.vehicle.getId(),
                seatIndex,
                this.movementType(),
                this.vehicle.getFirstPassenger() == null ? -1 : this.vehicle.getFirstPassenger().getId());
        return canDrive;
    }

    @Override
    public void drive(Mob mob, VehicleInput input) {
        if (!this.canDrive(mob)) {
            VehicleAiLogger.debugEvery(mob, 20, "SBW drive skipped: mob is not in a drive-capable seat on vehicle {}", this.vehicle.getId());
            return;
        }
        if (!CommonConfig.VEHICLE_AI_NEEDS_FUEL.get()) {
            this.refillVehicleEnergy();
        }
        short keys = toSbwKeys(input);
        if (input.brake() && this.vehicle.computed().getEngineType() == EngineType.WHEEL) {
            keys &= (short) ~(SBW_LEFT_KEY | SBW_RIGHT_KEY);
        }
        this.vehicle.processInput(keys);
        VehicleAiLogger.debugEvery(mob, 20, "SBW processInput vehicle={} seat={} keys={} input={} velocity={} energy={}",
                this.vehicle.getId(),
                this.vehicle.getSeatIndex(mob),
                keys,
                input,
                this.vehicle.getDeltaMovement(),
                this.vehicle.hasEnergyStorage() ? this.vehicle.getEnergy() : -1);
    }

    @Override
    public void controlFlight(Mob mob, VehicleFlightInput input) {
        if (!this.canDrive(mob)) {
            VehicleAiLogger.debugEvery(mob, 20, "SBW flight skipped: mob is not in a drive-capable seat on vehicle {}", this.vehicle.getId());
            return;
        }
        if (!CommonConfig.VEHICLE_AI_NEEDS_FUEL.get()) {
            this.refillVehicleEnergy();
        }
        this.vehicle.mouseInput(input.mouseX(), input.mouseY());
        short keys = this.toSbwFlightKeys(input);
        this.vehicle.processInput(keys);
        VehicleAiLogger.debugEvery(mob, 20,
                "SBW flightInput vehicle={} seat={} keys={} input={} mouseX={} mouseY={} rollTarget={} velocity={} hover={} power={} energy={}",
                this.vehicle.getId(),
                this.vehicle.getSeatIndex(mob),
                keys,
                input.input(),
                input.mouseX(),
                input.mouseY(),
                input.rollTarget(),
                this.vehicle.getDeltaMovement(),
                this.vehicle.getHoverMode(),
                this.vehicle.getPower(),
                this.vehicle.hasEnergyStorage() ? this.vehicle.getEnergy() : -1);
    }

    @Override
    public void initializeGeneratedSupplies() {
        if (CommonConfig.VEHICLE_AI_NEEDS_FUEL.get()) {
            this.refillVehicleEnergy();
        }
        if (this.vehicle.hasDecoy() && !this.vehicle.hasSmokeDecoy() && this.vehicle.getDecoyCount() == 0) {
            this.vehicle.setDecoyCount(this.vehicle.computed().getDecoyMagazineSize());
        }
        if (!CommonConfig.VEHICLE_AI_NEEDS_AMMO.get()) {
            return;
        }
        for (String weaponName : List.copyOf(this.vehicle.getGunDataMap().keySet())) {
            GunData data = this.vehicle.getGunData(weaponName);
            if (data == null || data.hasInfiniteBackupAmmo(this.vehicle)) {
                continue;
            }
            int magazine = data.useBackpackAmmo() ? 0 : Math.max(0, data.get(GunProp.MAGAZINE));
            int totalAmmo = initialAmmoUnits(initialCombatSeconds(this.payloadType(data, weaponName)),
                    this.weaponRpm(weaponName), this.weaponFireMode(data), this.actionCycleTicks(data), magazine,
                    data.get(GunProp.AMMO_COST_PER_SHOOT), data.get(GunProp.EMPTY_RELOAD_TIME));
            if (totalAmmo <= 0) {
                continue;
            }
            int loadedAmmo = Math.min(magazine, totalAmmo);
            this.vehicle.modifyGunData(weaponName, gun -> {
                gun.ammo.set(loadedAmmo);
                gun.virtualAmmo.set(totalAmmo - loadedAmmo);
            });
        }
    }

    @Override
    public boolean canDeployDecoy() {
        return !this.isWreck() && this.vehicle.hasDecoy() && !this.vehicle.hasSmokeDecoy()
                && this.vehicle.getDecoyCount() > 0;
    }

    @Override
    public boolean initializeAirborneHover(Mob mob) {
        if (!this.supportsFlightControl() || !this.canDrive(mob)) {
            return false;
        }
        if (!CommonConfig.VEHICLE_AI_NEEDS_FUEL.get()) {
            this.refillVehicleEnergy();
        }
        float power = Math.max(this.vehicle.getPower(), AIRBORNE_HOVER_POWER);
        this.vehicle.processInput((short) 0);
        this.vehicle.mouseInput(0.0D, 0.0D);
        this.vehicle.setDeltaMovement(Vec3.ZERO);
        this.vehicle.setXRot(0.0F);
        this.vehicle.setZRot(0.0F);
        this.vehicle.setDeltaRot(0.0F);
        this.vehicle.setHoverMode(true);
        this.vehicle.setEngineStart(true);
        this.vehicle.setEngineStartOver(true);
        this.vehicle.setHoldPowerTick(0);
        this.vehicle.setPower(power);
        this.vehicle.setSynchedPropellerRot(Math.max(this.vehicle.getSynchedPropellerRot(), power));
        VehicleAiLogger.debug(mob,
                "SBW airborne hover initialized vehicle={} power={} propeller={} hover={} energy={}",
                this.vehicle.getId(),
                this.vehicle.getPower(),
                this.vehicle.getSynchedPropellerRot(),
                this.vehicle.getHoverMode(),
                this.vehicle.hasEnergyStorage() ? this.vehicle.getEnergy() : -1);
        return true;
    }

    @Override
    public void stopDriving(Mob mob) {
        if (this.canDrive(mob)) {
            if (this.supportsFlightControl()) {
                this.vehicle.mouseInput(0.0D, 0.0D);
            }
            this.vehicle.processInput((short) 0);
            VehicleAiLogger.debugEvery(mob, 20, "SBW stopDriving vehicle={} seat={}", this.vehicle.getId(), this.vehicle.getSeatIndex(mob));
        }
    }

    @Override
    public List<WeaponHandle> weapons(Mob mob) {
        int seatIndex = this.vehicle.getSeatIndex(mob);
        if (seatIndex < 0 || seatIndex >= this.vehicle.getMaxPassengers()) {
            VehicleAiLogger.debugEvery(mob, 40, "SBW weapons empty: invalid seat {} on vehicle {}", seatIndex, this.vehicle.getId());
            return List.of();
        }
        SeatInfo seat = this.vehicle.computed().seats().get(seatIndex);
        List<String> weaponNames = seat.weapons();
        List<WeaponHandle> weapons = new ArrayList<>();
        int skippedRiderAimed = 0;
        for (int i = 0; i < weaponNames.size(); i++) {
            GunData data = this.vehicle.getGunData(seatIndex, i);
            if (data != null) {
                WeaponAimMode aimMode = this.weaponAimMode(seatIndex, data);
                if (aimMode == WeaponAimMode.RIDER_AIMED) {
                    skippedRiderAimed++;
                    continue;
                }
                WeaponPayloadType payloadType = this.payloadType(data, weaponNames.get(i));
                weapons.add(new WeaponHandle(i, weaponNames.get(i), 0.0D, 96.0D, i, aimMode, payloadType,
                        this.weaponFireMode(data),
                        this.weaponRpm(weaponNames.get(i)),
                        this.actionCycleTicks(data)));
            }
        }
        VehicleAiLogger.debugEvery(mob, 40,
                "SBW weapons vehicle={} seat={} names={} usable={} skippedRiderAimed={} aimModes={} payloads={} fireModes={} rpm={} actionCycleTicks={}",
                this.vehicle.getId(),
                seatIndex,
                weaponNames,
                weapons.size(),
                skippedRiderAimed,
                weapons.stream().map(WeaponHandle::aimMode).toList(),
                weapons.stream().map(WeaponHandle::payloadType).toList(),
                weapons.stream().map(WeaponHandle::fireMode).toList(),
                weapons.stream().map(WeaponHandle::rpm).toList(),
                weapons.stream().map(WeaponHandle::actionCycleTicks).toList());
        return weapons;
    }

    private double weaponRpm(String weaponName) {
        return Math.max(1.0D, this.vehicle.vehicleWeaponRpm(weaponName));
    }

    static double initialCombatSeconds(WeaponPayloadType payloadType) {
        return switch (payloadType) {
            case BULLET -> 90.0D;
            case HITSCAN -> 67.5D;
            case SHELL, UNKNOWN -> 45.0D;
            case ROCKET -> 30.0D;
            case MISSILE -> 60.0D;
            case UTILITY -> 0.0D;
        };
    }

    static int initialAmmoUnits(double combatSeconds, double rpm, WeaponFireMode fireMode,
                                double actionCycleTicks, int magazine, int ammoCost, int emptyReloadTicks) {
        if (combatSeconds <= 0.0D || ammoCost <= 0) {
            return 0;
        }
        boolean semiAutomatic = fireMode == WeaponFireMode.SEMI_AUTOMATIC || fireMode == WeaponFireMode.BURST;
        double interval = (semiAutomatic ? 2400.0D : 1200.0D) / Math.max(1.0D, rpm);
        interval = Math.max(interval, Math.max(0.0D, actionCycleTicks));
        double durationTicks = combatSeconds * 20.0D;
        double shots;
        int shotsPerMagazine = Math.max(0, magazine) / ammoCost;
        if (shotsPerMagazine == 0) {
            shots = Math.ceil(durationTicks / interval);
        } else {
            double cycleTicks = shotsPerMagazine * interval + Math.max(0, emptyReloadTicks);
            double fullCycles = Math.floor(durationTicks / cycleTicks);
            double remainingTicks = durationTicks - fullCycles * cycleTicks;
            shots = fullCycles * shotsPerMagazine
                    + Math.min(shotsPerMagazine, Math.ceil(remainingTicks / interval));
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(shots * ammoCost));
    }

    private double actionCycleTicks(GunData data) {
        int boltActionTicks = data.get(GunProp.BOLT_ACTION_TIME);
        return boltActionTicks > 0 ? boltActionTicks + 1.0D : 0.0D;
    }

    private WeaponFireMode weaponFireMode(GunData data) {
        FireMode mode = this.sbwFireMode(data);
        if (mode == FireMode.AUTO) {
            return WeaponFireMode.AUTOMATIC;
        }
        if (mode == FireMode.BURST) {
            return WeaponFireMode.BURST;
        }
        return WeaponFireMode.SEMI_AUTOMATIC;
    }

    private FireMode sbwFireMode(GunData data) {
        FireModeInfo info = data.selectedFireModeInfo();
        return info == null || info.mode == null ? FireMode.SEMI : info.mode;
    }

    private boolean hasMountedWeapon(int seatIndex) {
        if (!this.vehicle.hasWeapon(seatIndex) || seatIndex < 0 || seatIndex >= this.vehicle.computed().seats().size()) {
            return false;
        }
        List<String> weaponNames = this.vehicle.computed().seats().get(seatIndex).weapons();
        for (int weaponIndex = 0; weaponIndex < weaponNames.size(); weaponIndex++) {
            GunData data = this.vehicle.getGunData(seatIndex, weaponIndex);
            if (data != null && this.weaponAimMode(seatIndex, data) != WeaponAimMode.RIDER_AIMED) {
                return true;
            }
        }
        return false;
    }

    private WeaponAimMode weaponAimMode(int seatIndex, GunData data) {
        if (data != null && isRiderAimedWeapon(data)) {
            return WeaponAimMode.RIDER_AIMED;
        }
        boolean independentTurret = this.vehicle.hasTurret() && seatIndex == this.vehicle.getTurretControllerIndex();
        boolean independentPassengerWeapon = this.vehicle.hasPassengerWeaponStation()
                && seatIndex == this.vehicle.getPassengerWeaponStationControllerIndex();
        return independentTurret || independentPassengerWeapon
                ? WeaponAimMode.INDEPENDENT
                : WeaponAimMode.VEHICLE_FIXED;
    }

    @Override
    public WeaponStatus weaponStatus(Mob mob, WeaponHandle weapon) {
        int seatIndex = this.vehicle.getSeatIndex(mob);
        GunData data = seatIndex < 0 ? null : this.vehicle.getGunData(seatIndex, weapon.id());
        if (data == null) {
            VehicleAiLogger.debugEvery(mob, 20, "SBW weaponStatus empty: vehicle={} seat={} weapon={}", this.vehicle.getId(), seatIndex, weapon.id());
            return new WeaponStatus(false, false, false, false, false, false, 0);
        }
        boolean infiniteAmmo = !CommonConfig.VEHICLE_AI_NEEDS_AMMO.get();
        if (infiniteAmmo) {
            this.refillVehicleEnergy();
        }
        boolean reloading = data.reloading();
        boolean needsReload = data.shouldStartReloading(this.vehicle);
        boolean selected = this.vehicle.getWeaponIndex(seatIndex) == weapon.id();
        boolean hasAmmo = infiniteAmmo || data.hasEnoughAmmoToShoot(this.vehicle) || data.hasBackupAmmo(this.vehicle);
        boolean readyToShoot = data.canShoot(this.vehicle);
        boolean canFire = readyToShoot;
        int heat = this.vehicle.getWeaponHeat(seatIndex, weapon.id());
        boolean overheated = data.overHeat.get() || heat > 100;
        boolean coolingDown = hasAmmo && !needsReload && !reloading && overheated;
        int ammoCost = data.get(GunProp.AMMO_COST_PER_SHOOT);
        int ammoCount = this.vehicle.getAmmoCount(seatIndex);
        int backupAmmo = data.countBackupAmmo(this.vehicle);
        int magazine = data.get(GunProp.MAGAZINE);
        boolean backpackAmmo = data.useBackpackAmmo();
        boolean infiniteBackup = data.hasInfiniteBackupAmmo(this.vehicle);
        VehicleAiLogger.debugEvery(mob, 20,
                "SBW weaponStatus vehicle={} seat={} weapon={} selected={} canFire={} needsReload={} reloading={} coolingDown={} hasAmmo={} ammoCount={} ammoCost={} backupAmmo={} magazine={} backpackAmmo={} infiniteBackup={} heat={} overheated={} readyToShoot={} sbwCanShoot={}",
                this.vehicle.getId(),
                seatIndex,
                weapon.id(),
                selected,
                canFire,
                needsReload,
                reloading,
                coolingDown,
                hasAmmo,
                ammoCount,
                ammoCost,
                backupAmmo,
                magazine,
                backpackAmmo,
                infiniteBackup,
                heat,
                overheated,
                readyToShoot,
                this.vehicle.canShoot(mob));
        return new WeaponStatus(selected, canFire, needsReload, reloading, coolingDown, hasAmmo, heat);
    }

    @Override
    public boolean hasAnyWeaponAmmo(Mob mob) {
        if (!CommonConfig.VEHICLE_AI_NEEDS_AMMO.get()) {
            return true;
        }
        List<SeatInfo> seats = this.vehicle.computed().seats();
        boolean hasWeapon = false;
        for (int seatIndex = 0; seatIndex < seats.size(); seatIndex++) {
            List<String> weaponNames = seats.get(seatIndex).weapons();
            for (int weaponIndex = 0; weaponIndex < weaponNames.size(); weaponIndex++) {
                GunData data = this.vehicle.getGunData(seatIndex, weaponIndex);
                if (data == null) {
                    continue;
                }
                if (this.weaponAimMode(seatIndex, data) == WeaponAimMode.RIDER_AIMED) {
                    continue;
                }
                hasWeapon = true;
                boolean hasLoadedAmmo = data.hasEnoughAmmoToShoot(this.vehicle);
                boolean hasBackupAmmo = data.hasBackupAmmo(this.vehicle);
                if (hasLoadedAmmo || hasBackupAmmo) {
                    VehicleAiLogger.debugEvery(mob, 40,
                            "SBW vehicle ammo available vehicle={} seat={} weapon={} loaded={} backup={}",
                            this.vehicle.getId(),
                            seatIndex,
                            weaponIndex,
                            hasLoadedAmmo,
                            hasBackupAmmo);
                    return true;
                }
            }
        }
        VehicleAiLogger.debugEvery(mob, 20, "SBW vehicle ammo unavailable vehicle={} hasWeapon={}", this.vehicle.getId(), hasWeapon);
        return false;
    }

    @Override
    public boolean selectWeapon(Mob mob, WeaponHandle weapon) {
        int seatIndex = this.vehicle.getSeatIndex(mob);
        if (seatIndex < 0) {
            VehicleAiLogger.debugEvery(mob, 20, "SBW selectWeapon failed: invalid seat on vehicle {}", this.vehicle.getId());
            return false;
        }
        this.vehicle.changeWeapon(seatIndex, weapon.id(), false);
        boolean selected = this.vehicle.getWeaponIndex(seatIndex) == weapon.id();
        VehicleAiLogger.debugEvery(mob, 20, "SBW selectWeapon vehicle={} seat={} weapon={} result={}", this.vehicle.getId(), seatIndex, weapon.id(), selected);
        return selected;
    }

    @Override
    public Vec3 aimPoint(Mob mob, Entity target) {
        int seatIndex = this.vehicle.getSeatIndex(mob);
        int weaponIndex = seatIndex < 0 ? -1 : this.vehicle.getWeaponIndex(seatIndex);
        return this.resolveAimPoint(mob, target, weaponIndex, null);
    }

    @Override
    public Vec3 aimPoint(Mob mob, Entity target, WeaponHandle weapon) {
        return this.resolveAimPoint(mob, target, weapon.id(), null);
    }

    @Override
    public boolean aimAt(Mob mob, Entity target, Vec3 aimPoint) {
        int seatIndex = this.vehicle.getSeatIndex(mob);
        int weaponIndex = seatIndex < 0 ? -1 : this.vehicle.getWeaponIndex(seatIndex);
        GunData data = seatIndex < 0 || weaponIndex < 0 ? null : this.vehicle.getGunData(seatIndex, weaponIndex);
        if (seatIndex < 0 || target == null || data == null) {
            VehicleAiLogger.debugEvery(mob, 20,
                    "SBW aimAt failed vehicle={} seat={} weapon={} target={}",
                    this.vehicle.getId(),
                    seatIndex,
                    weaponIndex,
                    target == null ? -1 : target.getId());
            return false;
        }

        WeaponAimMode aimMode = this.weaponAimMode(seatIndex, data);
        String targetUuid = target.getStringUUID();
        boolean controlledBySbwTarget = false;
        if (aimMode == WeaponAimMode.INDEPENDENT && this.vehicle.hasTurret() && seatIndex == this.vehicle.getTurretControllerIndex()) {
            this.vehicle.setAiTurretTargetUUID(targetUuid);
            controlledBySbwTarget = true;
        } else if (aimMode == WeaponAimMode.INDEPENDENT && this.vehicle.hasPassengerWeaponStation() && seatIndex == this.vehicle.getPassengerWeaponStationControllerIndex()) {
            this.vehicle.setAiPassengerWeaponTargetUUID(targetUuid);
            controlledBySbwTarget = true;
        }

        VehicleAiLogger.debugEvery(mob, 20,
                "SBW aimAt vehicle={} seat={} weapon={} name={} target={} aimMode={} sbwTargetControl={} aimPoint={} targetUuid={} aiTurretTarget={} aiPassengerWeaponTarget={} aimDebug={}",
                this.vehicle.getId(),
                seatIndex,
                weaponIndex,
                this.weaponName(seatIndex, weaponIndex),
                target.getId(),
                aimMode,
                controlledBySbwTarget,
                aimPoint,
                targetUuid,
                this.vehicle.getAiTurretTargetUUID(),
                this.vehicle.getAiPassengerWeaponTargetUUID(),
                this.aimDebugSummary(mob, seatIndex, data));
        return controlledBySbwTarget || aimMode == WeaponAimMode.VEHICLE_FIXED;
    }

    @Override
    public boolean hasLineOfSight(Mob mob, Entity target, Vec3 aimPoint) {
        if (target == null) {
            return false;
        }

        Vec3 start = this.vehicle.getSeatIndex(mob) >= 0
                ? this.vehicle.getShootPos(mob, 1.0F)
                : this.vehicle.getBoundingBox().getCenter();
        Entity aimEntity = target.getVehicle() == null ? target : target.getVehicle();
        Vec3 end = aimPoint == null ? aimEntity.getBoundingBox().getCenter() : aimPoint;
        HitResult hit = this.vehicle.level().clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this.vehicle));
        boolean clear = hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(end) <= 0.25D;
        VehicleAiLogger.debugEvery(mob, 20,
                "SBW line of sight vehicle={} seat={} target={} clear={} start={} end={} hit={}",
                this.vehicle.getId(),
                this.vehicle.getSeatIndex(mob),
                target.getId(),
                clear,
                start,
                end,
                hit.getType());
        return clear;
    }

    @Override
    public boolean isAimedAt(Mob mob, Entity target, Vec3 aimPoint, WeaponHandle weapon) {
        double thresholdDegrees = this.fireAlignmentThreshold(weapon);
        return this.isWeaponAimedAt(mob, target, weapon.id(), thresholdDegrees, aimPoint);
    }

    @Override
    public WeaponFireWindow fireWindow(Mob mob, Entity target, Vec3 aimPoint, WeaponHandle weapon) {
        int seatIndex = this.vehicle.getSeatIndex(mob);
        if (seatIndex < 0 || target == null || weapon == null || this.vehicle.getGunData(seatIndex, weapon.id()) == null) {
            return WeaponFireWindow.unknown();
        }

        AimMetrics metrics = this.aimMetrics(mob, target, weapon.id(), aimPoint);
        if (!metrics.valid()) {
            return WeaponFireWindow.unknown();
        }

        FireWindowRange range = this.fireWindowRange(seatIndex, metrics.aimMode());
        if (!range.known()) {
            return metrics.angleDegrees() <= this.fireAlignmentThreshold(weapon)
                    ? WeaponFireWindow.readyNow(0.0D, 0.0D, metrics.estimatedMiss())
                    : WeaponFireWindow.unknown();
        }

        double targetYaw = relativeYawToVehicle(metrics.targetDirection());
        double targetPitch = sbwPitch(metrics.targetDirection());
        double yawError = range.errorToYaw(targetYaw);
        double pitchError = range.errorToPitch(targetPitch);
        WeaponFireWindow window;
        if (metrics.angleDegrees() <= this.fireAlignmentThreshold(weapon)) {
            window = WeaponFireWindow.readyNow(yawError, pitchError, metrics.estimatedMiss());
        } else if (metrics.aimMode() == WeaponAimMode.VEHICLE_FIXED) {
            window = WeaponFireWindow.needsPosture(yawError, pitchError, metrics.estimatedMiss());
        } else if (pitchError > 0.5D) {
            window = WeaponFireWindow.impossible(yawError, pitchError, metrics.estimatedMiss());
        } else if (yawError <= 0.5D) {
            window = WeaponFireWindow.inWindow(yawError, pitchError, metrics.estimatedMiss());
        } else {
            window = WeaponFireWindow.needsPosture(yawError, pitchError, metrics.estimatedMiss());
        }

        VehicleAiLogger.debugEvery(mob, 20,
                "SBW fire window vehicle={} seat={} weapon={} name={} target={} aimMode={} state={} targetYaw={} targetPitch={} yawRange=[{},{}] pitchRange=[{},{}] yawError={} pitchError={} shotAngleDeg={} estimatedMiss={}",
                this.vehicle.getId(),
                seatIndex,
                weapon.id(),
                weapon.name(),
                target.getId(),
                metrics.aimMode(),
                window.state(),
                targetYaw,
                targetPitch,
                range.minYaw(),
                range.maxYaw(),
                range.minPitch(),
                range.maxPitch(),
                yawError,
                pitchError,
                metrics.angleDegrees(),
                metrics.estimatedMiss());
        return window;
    }

    public boolean isCurrentWeaponAimedAt(Mob mob, Entity target) {
        int seatIndex = this.vehicle.getSeatIndex(mob);
        int weaponIndex = seatIndex < 0 ? -1 : this.vehicle.getWeaponIndex(seatIndex);
        GunData data = seatIndex < 0 || weaponIndex < 0 ? null : this.vehicle.getGunData(seatIndex, weaponIndex);
        double thresholdDegrees = this.fireAlignmentThreshold(data == null ? null : this.weaponAimMode(seatIndex, data));
        return this.isWeaponAimedAt(mob, target, weaponIndex, thresholdDegrees, this.resolveAimPoint(mob, target, weaponIndex, null));
    }

    private boolean isWeaponAimedAt(Mob mob, Entity target, int weaponIndex, double thresholdDegrees, Vec3 aimPoint) {
        int seatIndex = this.vehicle.getSeatIndex(mob);
        if (seatIndex < 0 || target == null || weaponIndex < 0 || this.vehicle.getGunData(seatIndex, weaponIndex) == null) {
            VehicleAiLogger.debugEvery(mob, 20, "SBW aim alignment failed: vehicle={} seat={} target={} weapon={}",
                    this.vehicle.getId(),
                    seatIndex,
                    target == null ? -1 : target.getId(),
                    weaponIndex);
            return false;
        }

        AimMetrics metrics = this.aimMetrics(mob, target, weaponIndex, aimPoint);
        if (!metrics.valid()) {
            return false;
        }

        boolean aligned = metrics.angleDegrees() <= thresholdDegrees;
        GunData data = this.vehicle.getGunData(seatIndex, weaponIndex);
        VehicleAiLogger.debugEvery(mob, 10,
                "SBW aim alignment vehicle={} seat={} selectedWeapon={} checkedWeapon={} weaponName={} target={} aimMode={} aimPoint={} aligned={} shotAngleDeg={} thresholdDeg={} shotDistance={} shotEstimatedMiss={} directionSource={} vehicleYaw={} mobYaw={} shootPos={} shootDir={} targetDir={} dot={} fireDirection={} fireDirectionForHud={} seekDirection={} aimDebug={}",
                this.vehicle.getId(),
                seatIndex,
                this.vehicle.getWeaponIndex(seatIndex),
                weaponIndex,
                this.weaponName(seatIndex, weaponIndex),
                target.getId(),
                metrics.aimMode(),
                metrics.aimPoint(),
                aligned,
                metrics.angleDegrees(),
                thresholdDegrees,
                metrics.distance(),
                metrics.estimatedMiss(),
                metrics.directionSource(),
                this.vehicle.getYRot(),
                mob.getYRot(),
                metrics.shootPosition(),
                metrics.shootDirection(),
                metrics.targetDirection(),
                metrics.dot(),
                directionSummary(data == null ? null : data.fireDirection()),
                directionSummary(data == null ? null : data.fireDirectionForHud()),
                seekDirectionSummary(data),
                this.aimDebugSummary(mob, seatIndex, data));
        return aligned;
    }

    private double fireAlignmentThreshold(WeaponHandle weapon) {
        return this.fireAlignmentThreshold(weapon == null ? null : weapon.aimMode());
    }

    private double fireAlignmentThreshold(WeaponAimMode aimMode) {
        if (aimMode == WeaponAimMode.VEHICLE_FIXED) {
            return FIXED_FIRE_ALIGNMENT_DEGREES;
        }
        if (aimMode == WeaponAimMode.RIDER_AIMED) {
            return RIDER_AIMED_FIRE_ALIGNMENT_DEGREES;
        }
        return INDEPENDENT_FIRE_ALIGNMENT_DEGREES;
    }

    private AimMetrics aimMetrics(Mob mob, Entity target, int weaponIndex, Vec3 aimPoint) {
        int seatIndex = this.vehicle.getSeatIndex(mob);
        GunData data = seatIndex < 0 || weaponIndex < 0 ? null : this.vehicle.getGunData(seatIndex, weaponIndex);
        if (seatIndex < 0 || target == null || weaponIndex < 0 || data == null) {
            return AimMetrics.INVALID;
        }

        WeaponAimMode aimMode = this.weaponAimMode(seatIndex, data);
        Vec3 shootDirection = this.fireAlignmentDirection(mob, seatIndex, aimMode);
        String directionSource = this.fireAlignmentDirectionSource(seatIndex, aimMode);
        Vec3 shootPosition = this.vehicle.getShootPos(mob, 1.0F);
        Vec3 resolvedAimPoint = this.resolveAimPoint(mob, target, weaponIndex, aimPoint);
        Vec3 targetDirection = resolvedAimPoint.subtract(shootPosition);
        if (aimMode == WeaponAimMode.INDEPENDENT) {
            BallisticAim targetAim = this.sbwIndependentBallisticAim(mob, target, weaponIndex, data);
            if (!targetAim.valid()) {
                return new AimMetrics(false, targetAim.aimPoint(), -1.0D, -1.0D, -1.0D,
                        directionSource + "+invalidSolution", aimMode, targetAim.shootPosition(), shootDirection,
                        targetAim.firingVector(), 0.0D);
            }
            resolvedAimPoint = targetAim.aimPoint();
            shootPosition = targetAim.shootPosition();
            targetDirection = targetAim.firingVector();
            directionSource = directionSource + "+sbwFiringSolution";
        }
        double distance = resolvedAimPoint.subtract(shootPosition).length();
        if (shootDirection.lengthSqr() < MIN_AIM_VECTOR_SQR || targetDirection.lengthSqr() < MIN_AIM_VECTOR_SQR) {
            return new AimMetrics(false, resolvedAimPoint, -1.0D, distance, -1.0D, directionSource,
                    aimMode, shootPosition, shootDirection, targetDirection, 0.0D);
        }

        double dot = shootDirection.normalize().dot(targetDirection.normalize());
        double clampedDot = Math.max(-1.0D, Math.min(1.0D, dot));
        double angleDegrees = Math.toDegrees(Math.acos(clampedDot));
        double estimatedMiss = Math.abs(Math.tan(Math.toRadians(angleDegrees))) * distance;
        return new AimMetrics(true, resolvedAimPoint, angleDegrees, distance, estimatedMiss, directionSource,
                aimMode, shootPosition, shootDirection, targetDirection, dot);
    }

    private BallisticAim sbwIndependentBallisticAim(Mob mob, Entity target, int weaponIndex, GunData data) {
        int seatIndex = this.vehicle.getSeatIndex(mob);
        String weaponName = this.weaponName(seatIndex, weaponIndex);
        Entity aimEntity = target.getVehicle() == null ? target : target.getVehicle();
        Vec3 aimPoint = aimEntity.getBoundingBox().getCenter();
        Vec3 targetVelocity = aimEntity.getDeltaMovement();
        if (aimEntity instanceof LivingEntity living) {
            targetVelocity = targetVelocity.add(0.0D, living.getAttributeValue(ForgeMod.ENTITY_GRAVITY.get()), 0.0D);
        }
        if (aimEntity instanceof Player) {
            targetVelocity = targetVelocity.multiply(2.0D, 1.0D, 2.0D);
        }

        Vec3 shootPosition = weaponName.isEmpty()
                ? this.vehicle.getShootPos(mob, 1.0F)
                : this.vehicle.getShootPos(weaponName, 1.0F);
        Vec3 shootVector = weaponName.isEmpty()
                ? this.vehicle.getShootVec(mob, 1.0F)
                : this.vehicle.getShootVec(weaponName, 1.0F);
        Vec3 launchPosition = shootPosition.subtract(shootVector.scale(shootPosition.distanceTo(mob.position())));
        Vec3 firingVector = RangeTool.calculateFiringSolution(
                launchPosition,
                aimPoint,
                targetVelocity,
                this.vehicle.getProjectileVelocity(data),
                this.vehicle.getProjectileGravity(data));
        boolean valid = isFiniteVector(firingVector) && firingVector.lengthSqr() >= MIN_AIM_VECTOR_SQR;
        return new BallisticAim(valid, aimPoint, shootPosition, firingVector);
    }

    private Vec3 fireAlignmentDirection(Mob mob, int seatIndex, WeaponAimMode aimMode) {
        if (aimMode == WeaponAimMode.RIDER_AIMED || aimMode == WeaponAimMode.INDEPENDENT) {
            return this.vehicle.getShootDirectionForHud(mob, 1.0F);
        }
        return this.vehicle.getShootVec(mob, 1.0F);
    }

    private String fireAlignmentDirectionSource(int seatIndex, WeaponAimMode aimMode) {
        if (aimMode == WeaponAimMode.RIDER_AIMED) {
            return "riderHudDirection";
        }
        if (aimMode == WeaponAimMode.INDEPENDENT) {
            return "sbwHudDirection";
        }
        return "shootVec";
    }

    private FireWindowRange fireWindowRange(int seatIndex, WeaponAimMode aimMode) {
        if (aimMode == WeaponAimMode.INDEPENDENT && this.vehicle.hasTurret() && seatIndex == this.vehicle.getTurretControllerIndex()) {
            return new FireWindowRange(
                    -this.vehicle.getTurretMaxYaw(),
                    -this.vehicle.getTurretMinYaw(),
                    -this.vehicle.getTurretMaxPitch(),
                    -this.vehicle.getTurretMinPitch());
        }
        if (aimMode == WeaponAimMode.INDEPENDENT
                && this.vehicle.hasPassengerWeaponStation()
                && seatIndex == this.vehicle.getPassengerWeaponStationControllerIndex()) {
            return new FireWindowRange(
                    -this.vehicle.getPassengerWeaponMaxYaw(),
                    -this.vehicle.getPassengerWeaponMinYaw(),
                    -this.vehicle.getPassengerWeaponMaxPitch(),
                    -this.vehicle.getPassengerWeaponMinPitch());
        }
        if (seatIndex >= 0 && seatIndex < this.vehicle.computed().seats().size()) {
            SeatInfo seat = this.vehicle.computed().seats().get(seatIndex);
            if (aimMode == WeaponAimMode.VEHICLE_FIXED) {
                return FireWindowRange.fixed();
            }
            return new FireWindowRange(
                    seat.getMinYaw(),
                    seat.getMaxYaw(),
                    -seat.getMaxPitch(),
                    -seat.getMinPitch());
        }
        return FireWindowRange.UNKNOWN;
    }

    private double relativeYawToVehicle(Vec3 direction) {
        return Mth.wrapDegrees((float) (sbwYaw(direction) - this.vehicle.getYRot()));
    }

    private static double sbwYaw(Vec3 direction) {
        return Math.toDegrees(Math.atan2(direction.x, direction.z));
    }

    private static double sbwPitch(Vec3 direction) {
        return Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()));
    }

    private static boolean isFiniteVector(Vec3 vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    private static boolean isRiderAimedWeapon(GunData data) {
        return isDirectionName(data.fireDirection(), "Passenger")
                || isDirectionName(data.fireDirectionForHud(), "Passenger")
                || hasPassengerSeekDirection(data);
    }

    private static boolean hasPassengerSeekDirection(GunData data) {
        try {
            SeekWeaponInfo seekWeaponInfo = data.get(GunProp.SEEK_WEAPON_INFO);
            return seekWeaponInfo != null && isDirectionName(seekWeaponInfo.getSeekDirection(), "Passenger");
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isDirectionName(StringOrVec3 direction, String name) {
        return direction != null
                && direction.isString()
                && name.equalsIgnoreCase(direction.getString());
    }

    private String aimDebugSummary(Mob mob, int seatIndex, GunData data) {
        return "turretController=" + this.vehicle.getTurretControllerIndex()
                + ",passengerWeaponController=" + this.vehicle.getPassengerWeaponStationControllerIndex()
                + ",seatIsTurret=" + (this.vehicle.hasTurret() && seatIndex == this.vehicle.getTurretControllerIndex())
                + ",seatIsPassengerWeapon=" + (this.vehicle.hasPassengerWeaponStation() && seatIndex == this.vehicle.getPassengerWeaponStationControllerIndex())
                + ",turretYaw=" + safeFloat(() -> this.vehicle.getTurretYaw(1.0F))
                + ",turretPitch=" + safeFloat(() -> this.vehicle.getTurretPitch(1.0F))
                + ",barrelYaw=" + safeFloat(() -> this.vehicle.getBarrelYRot(1.0F))
                + ",barrelPitch=" + safeFloat(() -> this.vehicle.getBarrelXRot(1.0F))
                + ",gunYaw=" + safeFloat(() -> this.vehicle.getGunYRot(1.0F))
                + ",gunPitch=" + safeFloat(() -> this.vehicle.getGunXRot(1.0F))
                + ",turretVec=" + safeVec(() -> this.vehicle.getTurretVector(1.0F))
                + ",barrelVec=" + safeVec(() -> this.vehicle.getBarrelVector(1.0F))
                + ",passengerWeaponVec=" + safeVec(() -> this.vehicle.getPassengerWeaponStationVector(1.0F))
                + ",shootVec=" + safeVec(() -> this.vehicle.getShootVec(mob, 1.0F))
                + ",hudDir=" + safeVec(() -> this.vehicle.getShootDirectionForHud(mob, 1.0F))
                + ",fireDirection=" + directionSummary(data == null ? null : data.fireDirection())
                + ",fireDirectionForHud=" + directionSummary(data == null ? null : data.fireDirectionForHud())
                + ",seekDirection=" + seekDirectionSummary(data);
    }

    private static String directionSummary(StringOrVec3 direction) {
        if (direction == null) {
            return "null";
        }
        if (direction.isString()) {
            return "string:" + direction.getString();
        }
        if (direction.isVec3()) {
            return "vec3:" + direction.getVec3();
        }
        return "unknown";
    }

    private static String seekDirectionSummary(GunData data) {
        if (data == null) {
            return "null";
        }
        try {
            SeekWeaponInfo seekWeaponInfo = data.get(GunProp.SEEK_WEAPON_INFO);
            return seekWeaponInfo == null ? "null" : directionSummary(seekWeaponInfo.getSeekDirection());
        } catch (RuntimeException ignored) {
            return "unavailable";
        }
    }

    private static String safeVec(VecSupplier supplier) {
        try {
            Vec3 value = supplier.get();
            return value == null ? "null" : value.toString();
        } catch (RuntimeException ignored) {
            return "unavailable";
        }
    }

    private static String safeFloat(FloatSupplier supplier) {
        try {
            return Float.toString(supplier.get());
        } catch (RuntimeException ignored) {
            return "unavailable";
        }
    }

    @Override
    public void stopWeaponControl(Mob mob) {
        int seatIndex = this.vehicle.getSeatIndex(mob);
        if (this.vehicle.hasTurret() && seatIndex == this.vehicle.getTurretControllerIndex()) {
            this.vehicle.setAiTurretTargetUUID(EMPTY_AI_TARGET);
        }
        if (this.vehicle.hasPassengerWeaponStation() && seatIndex == this.vehicle.getPassengerWeaponStationControllerIndex()) {
            this.vehicle.setAiPassengerWeaponTargetUUID(EMPTY_AI_TARGET);
        }
        VehicleAiLogger.debugEvery(mob, 20, "SBW stopWeaponControl vehicle={} seat={}", this.vehicle.getId(), seatIndex);
    }

    @Override
    public boolean fire(Mob mob, Entity target) {
        return this.fire(mob, target, this.aimPoint(mob, target), null);
    }

    @Override
    public boolean fire(Mob mob, Entity target, Vec3 aimPoint, WeaponHandle weapon) {
        int seatIndex = this.vehicle.getSeatIndex(mob);
        if (seatIndex < 0 || target == null) {
            VehicleAiLogger.debugEvery(mob, 20, "SBW fire failed: vehicle={} seat={} target={}", this.vehicle.getId(), seatIndex, target == null ? -1 : target.getId());
            return false;
        }
        if (!CommonConfig.VEHICLE_AI_NEEDS_AMMO.get()) {
            this.refillVehicleEnergy();
        }
        GunData data = this.vehicle.getGunData(seatIndex);
        boolean canShootBefore = data != null && this.vehicle.canShoot(mob);
        int heatBefore = this.vehicle.getWeaponHeat(seatIndex);
        int weaponIndex = weapon == null ? this.vehicle.getWeaponIndex(seatIndex) : weapon.id();
        boolean groundTarget = this.prefersGroundTarget(mob, target, weaponIndex);
        UUID targetUuid = groundTarget ? null : target.getUUID();
        Vec3 targetPos = groundTarget ? this.resolveAimPoint(mob, target, weaponIndex, aimPoint) : null;
        AimMetrics metrics = this.aimMetrics(mob, target, weaponIndex, aimPoint);
        double thresholdDegrees = this.fireAlignmentThreshold(weapon);
        if (!metrics.valid() || metrics.angleDegrees() > thresholdDegrees) {
            VehicleAiLogger.debugEvery(mob, 10,
                    "SBW fire blocked by aim alignment vehicle={} seat={} target={} groundTarget={} selectedWeapon={} checkedWeapon={} weaponName={} aimMode={} shotAngleDeg={} thresholdDeg={} shotDistance={} shotEstimatedMiss={} directionSource={} vehicleYaw={} mobYaw={} shootPos={} shootDir={} targetDir={} dot={}",
                    this.vehicle.getId(),
                    seatIndex,
                    target.getId(),
                    groundTarget,
                    this.vehicle.getWeaponIndex(seatIndex),
                    weaponIndex,
                    this.weaponName(seatIndex, weaponIndex),
                    metrics.aimMode(),
                    metrics.valid() ? metrics.angleDegrees() : -1.0D,
                    thresholdDegrees,
                    metrics.valid() ? metrics.distance() : -1.0D,
                    metrics.valid() ? metrics.estimatedMiss() : -1.0D,
                    metrics.directionSource(),
                    this.vehicle.getYRot(),
                    mob.getYRot(),
                    metrics.shootPosition(),
                    metrics.shootDirection(),
                    metrics.targetDirection(),
                    metrics.dot());
            return false;
        }
        if (!canShootBefore) {
            VehicleAiLogger.debugEvery(mob, 10,
                    "SBW fire skipped vehicle={} seat={} target={} groundTarget={} heat={} selectedWeapon={} shotAngleDeg={} shotDistance={} shotEstimatedMiss={}",
                    this.vehicle.getId(),
                    seatIndex,
                    target.getId(),
                    groundTarget,
                    heatBefore,
                    this.vehicle.getWeaponIndex(seatIndex),
                    metrics.valid() ? metrics.angleDegrees() : -1.0D,
                    metrics.valid() ? metrics.distance() : -1.0D,
                    metrics.valid() ? metrics.estimatedMiss() : -1.0D);
            return false;
        }
        this.vehicle.vehicleShoot(mob, targetUuid, targetPos);
        int heatAfter = this.vehicle.getWeaponHeat(seatIndex);
        VehicleAiLogger.debug(mob,
                "SBW fire called vehicle={} seat={} target={} aimPoint={} groundTarget={} canShootBefore={} heatBefore={} heatAfter={} selectedWeapon={} checkedWeapon={} weaponName={} aimMode={} shotAngleDeg={} shotDistance={} shotEstimatedMiss={} directionSource={} vehicleYaw={} mobYaw={} shootPos={} shootDir={} targetDir={} dot={}",
                this.vehicle.getId(),
                seatIndex,
                target.getId(),
                targetPos,
                groundTarget,
                canShootBefore,
                heatBefore,
                heatAfter,
                this.vehicle.getWeaponIndex(seatIndex),
                weaponIndex,
                this.weaponName(seatIndex, weaponIndex),
                metrics.aimMode(),
                metrics.valid() ? metrics.angleDegrees() : -1.0D,
                metrics.valid() ? metrics.distance() : -1.0D,
                metrics.valid() ? metrics.estimatedMiss() : -1.0D,
                metrics.directionSource(),
                this.vehicle.getYRot(),
                mob.getYRot(),
                metrics.shootPosition(),
                metrics.shootDirection(),
                metrics.targetDirection(),
                metrics.dot());
        return canShootBefore;
    }

    @Override
    public boolean reload(Mob mob) {
        int seatIndex = this.vehicle.getSeatIndex(mob);
        if (seatIndex < 0) {
            VehicleAiLogger.debugEvery(mob, 20, "SBW reload failed: invalid seat on vehicle {}", this.vehicle.getId());
            return false;
        }
        this.vehicle.modifyGunData(seatIndex, GunData::startReload);
        VehicleAiLogger.debug(mob, "SBW reload called vehicle={} seat={}", this.vehicle.getId(), seatIndex);
        return true;
    }

    private void refillVehicleEnergy() {
        if (this.vehicle.hasEnergyStorage()) {
            this.vehicle.setEnergy(this.vehicle.getMaxEnergy());
        }
    }

    private Vec3 resolveAimPoint(Mob mob, Entity target, int weaponIndex, Vec3 fallback) {
        if (target == null) {
            return fallback == null ? Vec3.ZERO : fallback;
        }
        if (this.prefersGroundTarget(mob, target, weaponIndex)) {
            return this.groundAimPoint(target);
        }
        if (fallback != null) {
            return fallback;
        }
        Entity aimEntity = target.getVehicle() == null ? target : target.getVehicle();
        return aimEntity.getBoundingBox().getCenter();
    }

    private Vec3 groundAimPoint(Entity target) {
        Entity aimEntity = target.getVehicle() == null ? target : target.getVehicle();
        AABB box = aimEntity.getBoundingBox();
        Vec3 start = new Vec3(box.getCenter().x, box.minY + 0.35D, box.getCenter().z);
        Vec3 end = start.add(0.0D, -64.0D, 0.0D);
        HitResult hit = this.vehicle.level().clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this.vehicle));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation().add(0.0D, 0.08D, 0.0D);
        }
        return new Vec3(box.getCenter().x, box.minY + 0.08D, box.getCenter().z);
    }

    private boolean prefersGroundTarget(Mob mob, Entity target, int weaponIndex) {
        if (target == null || !target.getRootVehicle().onGround()) {
            return false;
        }
        int seatIndex = this.vehicle.getSeatIndex(mob);
        if (seatIndex < 0 || weaponIndex < 0) {
            return false;
        }
        GunData data = this.vehicle.getGunData(seatIndex, weaponIndex);
        if (data == null) {
            return false;
        }
        return this.payloadType(data, this.weaponName(seatIndex, weaponIndex)).isHeavyTargetPreferred();
    }

    private WeaponPayloadType payloadType(GunData data, String weaponName) {
        String projectile = this.projectileName(data).toLowerCase(Locale.ROOT);
        String weapon = weaponName == null ? "" : weaponName.toLowerCase(Locale.ROOT);
        String shellType = this.shellType(data);
        GunType gunType = this.gunType(data);
        SeekType seekType = this.seekType(data);

        if ("empty".equals(projectile)) {
            return WeaponPayloadType.UTILITY;
        }
        if ("ray".equals(projectile)) {
            return WeaponPayloadType.HITSCAN;
        }
        if (seekType != SeekType.NONE
                || containsAny(projectile, "missile", "agm", "kh_39", "igla", "javelin", "wire_guide")
                || containsAny(weapon, "missile", "agm", "atgm", "tow", "igla", "javelin")) {
            return WeaponPayloadType.MISSILE;
        }
        if (containsAny(projectile, "rocket", "rpg")
                || containsAny(weapon, "rocket", "rpg")) {
            return WeaponPayloadType.ROCKET;
        }
        if (isShellType(shellType)
                || containsAny(projectile, "cannon_shell", "mortar_shell", "shell")
                || containsAny(weapon, "cannon", "mortar", "howitzer", "artillery")) {
            return WeaponPayloadType.SHELL;
        }
        if (gunType == GunType.DIRECT_LAUNCHER) {
            return this.hasExplosion(data) ? WeaponPayloadType.ROCKET : WeaponPayloadType.SHELL;
        }
        if (gunType == GunType.CURVED_LAUNCHER) {
            return WeaponPayloadType.SHELL;
        }
        if (containsAny(projectile, "projectile", "bullet")
                || gunType == GunType.RIFLE
                || gunType == GunType.SHOTGUN
                || gunType == GunType.SNIPER
                || gunType == GunType.MACHINE_GUN
                || gunType == GunType.HANDGUN
                || gunType == GunType.SMG) {
            return WeaponPayloadType.BULLET;
        }
        return WeaponPayloadType.UNKNOWN;
    }

    private GunType gunType(GunData data) {
        try {
            return data.get(GunProp.GUN_TYPE);
        } catch (RuntimeException ignored) {
            return GunType.SPECIAL;
        }
    }

    private SeekType seekType(GunData data) {
        try {
            return data.get(GunProp.SEEK_TYPE);
        } catch (RuntimeException ignored) {
            return SeekType.NONE;
        }
    }

    private String shellType(GunData data) {
        try {
            return data.get(GunProp.SHELL_TYPE);
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private String weaponName(int seatIndex, int weaponIndex) {
        if (seatIndex < 0 || seatIndex >= this.vehicle.computed().seats().size()) {
            return "";
        }
        List<String> weapons = this.vehicle.computed().seats().get(seatIndex).weapons();
        return weaponIndex >= 0 && weaponIndex < weapons.size() ? weapons.get(weaponIndex) : "";
    }

    private String projectileName(GunData data) {
        try {
            ProjectileInfo projectile = data.get(GunProp.PROJECTILE);
            return projectile == null ? "" : projectile.getItemId();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private boolean hasExplosion(GunData data) {
        try {
            return data.get(GunProp.EXPLOSION_DAMAGE) > 0.0D || data.get(GunProp.EXPLOSION_RADIUS) > 0.0D;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isShellType(String shellType) {
        String text = shellType == null ? "" : shellType.trim();
        return !text.isEmpty() && !"Default".equalsIgnoreCase(text);
    }

    private static boolean containsAny(String value, String... needles) {
        String text = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static short toSbwKeys(VehicleInput input) {
        int keys = 0;
        if (input.left()) {
            keys |= SBW_LEFT_KEY;
        }
        if (input.right()) {
            keys |= SBW_RIGHT_KEY;
        }
        if (input.forward()) {
            keys |= 0b000000100;
        }
        if (input.back()) {
            keys |= 0b000001000;
        }
        if (input.up() || input.brake()) {
            keys |= 0b000010000;
        }
        if (input.down()) {
            keys |= 0b000100000;
        }
        if (input.decoy()) {
            keys |= 0b001000000;
        }
        if (input.fire()) {
            keys |= 0b010000000;
        }
        if (input.sprint()) {
            keys |= 0b100000000;
        }
        return (short) keys;
    }

    private short toSbwFlightKeys(VehicleFlightInput input) {
        short keys = toSbwKeys(input.input());
        double mouseRollDemand = 0.25D * input.mouseX() * this.vehicle.getSynchedPropellerRot();
        double targetDeltaRot = -mouseRollDemand * FLIGHT_ROLL_COUNTER_RATIO;
        double deltaRot = this.vehicle.getDeltaRot();
        if (Double.isFinite(input.rollTarget())
                && this.vehicle.getEngineInfo() instanceof EngineInfo.Helicopter engine) {
            double rollSpeed = engine.getRollSpeed() * (this.vehicle.getHoverMode() ? 0.05D : 1.0D);
            double retainedRoll = this.vehicle.getRoll() * (this.vehicle.getHoverMode() ? 0.9603D : 0.99D);
            targetDeltaRot -= Mth.clamp(Mth.wrapDegrees(input.rollTarget() - retainedRoll)
                    / Math.max(0.01D, rollSpeed * 4.0D), -7.0D, 7.0D);
            keys &= (short) ~(SBW_LEFT_KEY | SBW_RIGHT_KEY);
            if (deltaRot < targetDeltaRot - 0.05D) keys |= SBW_LEFT_KEY;
            if (deltaRot > targetDeltaRot + 0.05D) keys |= SBW_RIGHT_KEY;
            return keys;
        }
        if (input.input().left() && deltaRot >= targetDeltaRot) {
            keys &= (short) ~SBW_LEFT_KEY;
        }
        if (input.input().right() && deltaRot <= targetDeltaRot) {
            keys &= (short) ~SBW_RIGHT_KEY;
        }
        return keys;
    }

    @FunctionalInterface
    private interface VecSupplier {
        Vec3 get();
    }

    @FunctionalInterface
    private interface FloatSupplier {
        float get();
    }

    private record AimMetrics(boolean valid, Vec3 aimPoint, double angleDegrees, double distance, double estimatedMiss,
                              String directionSource, WeaponAimMode aimMode, Vec3 shootPosition, Vec3 shootDirection,
                              Vec3 targetDirection, double dot) {
        private static final AimMetrics INVALID = new AimMetrics(false, Vec3.ZERO, -1.0D, -1.0D, -1.0D,
                "invalid", null, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0.0D);
    }

    private record BallisticAim(boolean valid, Vec3 aimPoint, Vec3 shootPosition, Vec3 firingVector) {
    }

    private record FireWindowRange(double minYaw, double maxYaw, double minPitch, double maxPitch) {
        private static final FireWindowRange UNKNOWN = new FireWindowRange(Double.NaN, Double.NaN, Double.NaN, Double.NaN);

        static FireWindowRange fixed() {
            return new FireWindowRange(-180.0D, 180.0D, -90.0D, 90.0D);
        }

        boolean known() {
            return !Double.isNaN(this.minYaw)
                    && !Double.isNaN(this.maxYaw)
                    && !Double.isNaN(this.minPitch)
                    && !Double.isNaN(this.maxPitch);
        }

        double errorToYaw(double yaw) {
            return rangeError(yaw, this.minYaw, this.maxYaw);
        }

        double errorToPitch(double pitch) {
            return linearRangeError(pitch, this.minPitch, this.maxPitch);
        }

        private static double rangeError(double angle, double min, double max) {
            if (max - min >= 359.0D) {
                return 0.0D;
            }
            double wrappedAngle = Mth.wrapDegrees((float) angle);
            double wrappedMin = Mth.wrapDegrees((float) min);
            double wrappedMax = Mth.wrapDegrees((float) max);
            if (wrappedMin <= wrappedMax) {
                return linearRangeError(wrappedAngle, wrappedMin, wrappedMax);
            }
            if (wrappedAngle >= wrappedMin || wrappedAngle <= wrappedMax) {
                return 0.0D;
            }
            return Math.min(Math.abs(Mth.wrapDegrees((float) (wrappedAngle - wrappedMin))),
                    Math.abs(Mth.wrapDegrees((float) (wrappedAngle - wrappedMax))));
        }

        private static double linearRangeError(double value, double min, double max) {
            double low = Math.min(min, max);
            double high = Math.max(min, max);
            if (value < low) {
                return low - value;
            }
            if (value > high) {
                return value - high;
            }
            return 0.0D;
        }
    }
}
