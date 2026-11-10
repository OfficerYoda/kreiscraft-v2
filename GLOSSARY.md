# Kreiscraft

Kreiscraft provides server-wide gameplay features and player-specific preferences for the
Kreiscraft Minecraft server.

## Language

**Player Settings**:
Preferences owned by an individual player, opened through `/settings` and applied to all output
addressed to that player.
_Avoid_: Server settings, module config

**Server Configuration**:
Operator-owned settings that control the plugin and its modules. Administrators reach these from
the Player Settings GUI or its dedicated command route.
_Avoid_: Player settings

**Pending Bedrock Authorization**:
A name-based authorization for a Bedrock player who has not joined before; it becomes bound to the
player's observed UUID on first matching login.
_Avoid_: Guessed UUID, known player

**Command Message Style**:
The consistent visual roles used across command output, including ordinary text, player names,
reasons, success, warnings, and errors.
_Avoid_: Per-command colors

**Spawn Radius**:
The single configured distance from the server spawn used to bound both spawn access and glide
eligibility; its default is 50 blocks.
_Avoid_: Teleport range, glide radius

**Spawn Access Zone**:
The vertical cylinder centered on server spawn whose horizontal radius is the Spawn Radius. A
player must be inside this zone to use `/spawn`.
_Avoid_: Teleport range

**Glide Zone**:
The three-dimensional sphere centered on server spawn whose radius is the Spawn Radius. A player
must be inside this zone to initiate spawn gliding.
_Avoid_: Spawn access zone

**Activity Data**:
Persistent player or gameplay records produced while the server runs, such as jail entries,
positions, Bedrock identities, and anti-cheat logs.
_Avoid_: Server configuration

**Start Sequence**:
The one-time ceremony that shows a full countdown and then begins one continuous world-border
expansion.
_Avoid_: Border phases

**Dimension Access**:
Whether portal travel into or out of the Nether or End is currently allowed. It is operator-owned
Server Configuration, editable through `/dimension` and the Server Configuration GUI, and it blocks
only portal travel.
_Avoid_: World access, world enabled

**Encounter**:
One attempt at The Shattered Crown dragon fight, ending in victory or reset.
_Avoid_: Raid instance, fight session

**Anchor**:
A destructible crystal objective whose survival shields the dragon and whose defenders must be
defeated before it can be destroyed.
_Avoid_: Shield generator, pylon

**Anchor Wave**:
A set of four independently defended Anchors that temporarily shields the dragon until every crystal
is destroyed. Completing a wave permanently unlocks additional attacks.
_Avoid_: Final pair, defender wave

**Roster Unit**:
One complete set of a wave's defenders assigned to an Anchor. Population scaling repeats whole units,
not individual defenders or groups within a unit.
_Avoid_: Defender count, group count

**Defender Group**:
A mounted pair or passenger tower within a Roster Unit. Every surviving member remains a defender
of the same Anchor even when the group separates.
_Avoid_: Roster Unit, extra life

**Attack Warning**:
A visible cue identifying an impending Encounter attack before it deals damage.
_Avoid_: Telegraph, attack notification

**Combat Population**:
Living Survival and Adventure players participating inside the Encounter's combat area, excluding
protected arrivals.
_Avoid_: Online population, wave population

**Scaling Population**:
Everyone connected in the managed End, including observers and players away from combat. A wave's
population is frozen when that wave starts; the opening wave instead uses everyone online server-wide
when the first player arrives in the End.
_Avoid_: Combat population, eligible targets

**Opening Grace**:
The preparation period at the start of a fresh Encounter when defenders are active but the dragon
circles without attacking.
_Avoid_: Arrival protection, attack cooldown
