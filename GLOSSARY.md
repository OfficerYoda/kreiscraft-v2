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
