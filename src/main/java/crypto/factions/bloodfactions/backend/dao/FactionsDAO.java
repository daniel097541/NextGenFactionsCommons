package crypto.factions.bloodfactions.backend.dao;

import crypto.factions.bloodfactions.commons.logger.Logger;
import crypto.factions.bloodfactions.commons.model.faction.Faction;
import crypto.factions.bloodfactions.commons.model.faction.FactionImpl;
import crypto.factions.bloodfactions.commons.model.faction.SystemFactionImpl;
import crypto.factions.bloodfactions.commons.model.invitation.FactionInvitation;
import crypto.factions.bloodfactions.commons.model.invitation.FactionInvitationImpl;
import crypto.factions.bloodfactions.commons.model.land.FChunk;
import crypto.factions.bloodfactions.commons.model.land.FLocation;
import crypto.factions.bloodfactions.commons.model.land.impl.FChunkImpl;
import crypto.factions.bloodfactions.commons.model.land.impl.FLocationImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public interface FactionsDAO extends DAO<Faction> {

    @Override
    default @NotNull Set<Faction> getSetFromResultSet(@NotNull ResultSet rs) {
        Set<Faction> factions = new HashSet<>();
        try {
            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("id"));
                String name = rs.getString("name");
                boolean isSystemFaction = rs.getBoolean("system_faction");
                UUID ownerId = UUID.fromString(rs.getString("owner_id"));

                // Returns normal faction.
                if (!isSystemFaction) {
                    factions.add(new FactionImpl(id, name, ownerId));
                }
                // Returns sys faction.
                else {
                    factions.add(new SystemFactionImpl(id, name, "&7", ownerId));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return factions;
    }

    /**
     * Creates the chunk in the database if it is not in it yet.
     *
     * @param chunk
     */
    default void insertChunkIfNotExists(@NotNull FChunk chunk) {
        String chunkInsertion = "INSERT INTO claims (id, x, z, world_id) SELECT * FROM ( SELECT ?, ?, ?, ?) AS tmp " +
                "WHERE NOT EXISTS ( SELECT id FROM claims WHERE id = ?) LIMIT 1;";
        try (PreparedStatement statement = this.getPreparedStatement(chunkInsertion)) {
            statement.setString(1, chunk.getId());
            statement.setInt(2, chunk.getX());
            statement.setInt(3, chunk.getZ());
            statement.setString(4, chunk.getWorldId().toString());
            statement.setString(5, chunk.getId());

            statement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Claims the chunk for the faction.
     *
     * @param factionId
     * @param chunk
     * @param claimerId
     * @return
     */
    default boolean claimForFaction(@NotNull UUID factionId, @NotNull FChunk chunk, @NotNull UUID claimerId) {

        this.insertChunkIfNotExists(chunk);

        String sql = "INSERT INTO as_faction_claims (faction_id, claim_id, claimed_by) VALUES (?,?,?);";

        try (PreparedStatement statement = this.getPreparedStatement(sql)) {

            statement.setString(1, factionId.toString());
            statement.setString(2, chunk.getId());
            statement.setString(3, claimerId.toString());

            statement.executeUpdate();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Gets the faction that owns this chunk.
     *
     * @param chunkId
     * @return
     */
    default Faction getFactionAtChunk(@NotNull String chunkId) {
        String sql = "SELECT * FROM as_faction_claims AS rel" +
                " JOIN factions AS f ON f.id = rel.faction_id " +
                " WHERE rel.claim_id = ?;";

        try (PreparedStatement preparedStatement = this.getPreparedStatement(sql)) {
            preparedStatement.setString(1, chunkId);
            try (ResultSet rs = preparedStatement.executeQuery()) {
                return fromResultSet(rs);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    default @Nullable Faction fromResultSet(@NotNull ResultSet rs) {
        try {
            if (rs.next()) {
                UUID id = UUID.fromString(rs.getString("id"));
                String name = rs.getString("name");
                boolean isSystemFaction = rs.getBoolean("system_faction");
                UUID ownerId = UUID.fromString(rs.getString("owner_id"));

                // Returns normal faction.
                if (!isSystemFaction) {
                    return new FactionImpl(id, name, ownerId);
                }
                // Returns sys faction.
                else {
                    return new SystemFactionImpl(id, name, "&7", ownerId);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Gets the faction of a player.
     *
     * @param id
     * @return
     */
    default Faction getFactionOfPlayer(@NotNull UUID id) {
        String sql = "SELECT * FROM as_faction_players AS rel " +
                " JOIN factions AS f ON f.id = rel.faction_id " +
                " WHERE rel.player_id = ?;";

        try (PreparedStatement preparedStatement = this.getPreparedStatement(sql)) {

            preparedStatement.setString(1, id.toString());

            try (ResultSet rs = preparedStatement.executeQuery()) {
                return this.fromResultSet(rs);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Removes all claims from the faction.
     *
     * @param factionId
     */
    default boolean removeAllClaimsOfFaction(@NotNull UUID factionId) {

        String sql = "DELETE FROM as_faction_claims WHERE faction_id = ?;";

        try (PreparedStatement statement = this.getPreparedStatement(sql)) {
            statement.setString(1, factionId.toString());

            int removed = statement.executeUpdate();
            return removed > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Gets the count of claims of a faction.
     *
     * @param factionId
     * @return
     */
    default int getCountOfClaims(@NotNull UUID factionId) {
        String sql = "SELECT count(*) AS count FROM as_faction_claims WHERE faction_id = ?;";
        int count = -1;

        try (PreparedStatement statement = this.getPreparedStatement(sql)) {
            statement.setString(1, factionId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    count = rs.getInt("count");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return count;
    }

    /**
     * Removes a claim from the faction.
     *
     * @param factionId
     * @param chunkId
     * @return
     */
    default boolean removeClaim(UUID factionId, String chunkId) {

        String sql = "DELETE FROM as_faction_claims WHERE faction_id = ? AND claim_id = ?;";

        try (PreparedStatement statement = this.getPreparedStatement(sql)) {
            statement.setString(1, factionId.toString());
            statement.setString(2, chunkId);

            int deleted = statement.executeUpdate();
            return deleted > 0;
        } catch (Exception e) {
            Logger.logError("Error removing claim.", e);
        }

        return false;
    }

    default @Nullable FLocation getCore(UUID factionId) {

        String sql = "SELECT * FROM factions_tps WHERE faction_id = ? AND is_core = 1;";

        try (PreparedStatement statement = this.getPreparedStatement(sql)) {

            statement.setString(1, factionId.toString());

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {

                    UUID worldId = UUID.fromString(rs.getString("world_id"));
                    int x = rs.getInt("x");
                    int y = rs.getInt("y");
                    int z = rs.getInt("z");

                    return new FLocationImpl(worldId, x, y, z);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    default boolean removeCore(@NotNull UUID factionId) {
        String sql = "DELETE FROM factions_tps WHERE faction_id = ? AND is_core = 1;";
        try (PreparedStatement statement = this.getPreparedStatement(sql)) {
            statement.setString(1, factionId.toString());
            int deleted = statement.executeUpdate();
            return deleted > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    default boolean setCore(@NotNull UUID factionId, @NotNull UUID playerId, @NotNull FLocation core) {

        String sql = "INSERT INTO factions_tps (faction_id, world_id, x, y, z, created_by, is_core) VALUES (?, ?, ?, ?, ?, ?, ?);";

        try (PreparedStatement statement = this.getPreparedStatement(sql)) {

            statement.setString(1, factionId.toString());
            statement.setString(2, core.getWorldId().toString());
            statement.setInt(3, core.getX());
            statement.setInt(4, core.getY());
            statement.setInt(5, core.getZ());
            statement.setString(6, playerId.toString());
            statement.setBoolean(7, true);

            return statement.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    default Set<FChunk> getAllClaimsOfFaction(UUID factionId) {

        String sql = "SELECT * FROM as_faction_claims AS rel " +
                " JOIN claims AS c ON c.id = rel.claim_id " +
                "WHERE rel.faction_id = ?;";

        Set<FChunk> claims = new HashSet<>();

        try (PreparedStatement statement = this.getPreparedStatement(sql)) {

            statement.setString(1, factionId.toString());

            try (ResultSet rs = statement.executeQuery()) {

                while (rs.next()) {

                    UUID worldId = UUID.fromString(rs.getString("world_id"));
                    int x = rs.getInt("x");
                    int z = rs.getInt("z");

                    FChunk claim = new FChunkImpl(worldId, x, z);
                    claims.add(claim);
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return claims;
    }

    default boolean deInvitePlayerFromFaction(@NotNull UUID factionId, @NotNull UUID playerId) {
        String sql = "DELETE FROM faction_invitations WHERE faction_id = ? AND player_id = ?;";

        try (PreparedStatement statement = this.getPreparedStatement(sql)) {

            statement.setString(1, factionId.toString());
            statement.setString(2, playerId.toString());

            return statement.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    default @Nullable FactionInvitation invitePlayerToFaction(@NotNull UUID factionId, @NotNull UUID playerId, @NotNull UUID inviterId) {

        String sql = "INSERT INTO faction_invitations (faction_id, player_id, inviter_id) VALUES (?, ?, ?);";

        try (PreparedStatement statement = this.getPreparedStatement(sql)) {

            statement.setString(1, factionId.toString());
            statement.setString(2, playerId.toString());
            statement.setString(3, inviterId.toString());

            boolean inserted = statement.executeUpdate() > 0;

            if (inserted) {
                return new FactionInvitationImpl(factionId, playerId, inviterId, new Date());
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    default boolean isPlayerInvitedToFaction(UUID factionId, UUID playerId) {

        String sql = "SELECT count(*) AS count FROM faction_invitations WHERE faction_id = ? AND player_id = ?;";

        try (PreparedStatement statement = this.getPreparedStatement(sql)) {

            statement.setString(1, factionId.toString());
            statement.setString(2, playerId.toString());

            try (ResultSet rs = statement.executeQuery()) {
                int count = rs.getInt("count");
                return count > 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    default @NotNull Set<FactionInvitation> getInvitationsOfFaction(@NotNull UUID factionId) {
        Set<FactionInvitation> invitations = new HashSet<>();

        String sql = "SELECT * FROM faction_invitations WHERE faction_id = ?;";

        try (PreparedStatement statement = this.getPreparedStatement(sql)) {

            statement.setString(1, factionId.toString());

            try (ResultSet rs = statement.executeQuery()) {

                while (rs.next()) {
                    UUID invitedPlayerId = UUID.fromString(rs.getString("player_id"));
                    UUID inviterId = UUID.fromString(rs.getString("player_id"));
                    Date invitedOn = rs.getDate("date");
                    invitations.add(new FactionInvitationImpl(factionId, invitedPlayerId, inviterId, invitedOn));
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return invitations;
    }

    default @NotNull Set<FactionInvitation> getInvitationsOfPlayer(@NotNull UUID playerId) {
        Set<FactionInvitation> invitations = new HashSet<>();

        String sql = "SELECT * FROM faction_invitations WHERE player_id = ?;";

        try (PreparedStatement statement = this.getPreparedStatement(sql)) {

            statement.setString(1, playerId.toString());

            try (ResultSet rs = statement.executeQuery()) {

                while (rs.next()) {
                    UUID factionId = UUID.fromString(rs.getString("faction_id"));
                    UUID inviterId = UUID.fromString(rs.getString("player_id"));
                    Date invitedOn = rs.getDate("date");
                    invitations.add(new FactionInvitationImpl(factionId, playerId, inviterId, invitedOn));
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return invitations;
    }
}
