#!/bin/bash
set -e

DB_USER="ladder"
DB_PASS="discPwd#!"
DB_NAME="discladder"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL

CREATE USER $DB_USER;
ALTER USER $DB_USER PASSWORD '$DB_PASS';
CREATE DATABASE $DB_NAME;
GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;

\c $DB_NAME

-- Grant permissions to use the public schema
GRANT ALL ON SCHEMA public TO $DB_USER;
GRANT ALL ON ALL TABLES IN SCHEMA public TO $DB_USER;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO $DB_USER;
GRANT ALL ON ALL FUNCTIONS IN SCHEMA public TO $DB_USER;
EOSQL

PGPASSWORD=$DB_PASS psql -U $DB_USER -d $DB_NAME -v ON_ERROR_STOP=1 <<'EOSQL'

CREATE TABLE players (
    player_id SERIAL PRIMARY KEY,
    player_name VARCHAR(100) UNIQUE NOT NULL,
    discord_id TEXT UNIQUE NOT NULL,
    active BOOLEAN DEFAULT FALSE,
    fafName VARCHAR(100) UNIQUE,
    current_team TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE teams (
    team_id SERIAL PRIMARY KEY,
    player_one_id INTEGER REFERENCES players(player_id),
    player_two_id INTEGER REFERENCES players(player_id),
    team_name VARCHAR(100),
    team_description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (player_one_id, player_two_id)
);

CREATE TABLE matches (
    match_id SERIAL PRIMARY KEY,
    team_one_id INTEGER REFERENCES teams(team_id),
    team_two_id INTEGER REFERENCES teams(team_id),
    winner INTEGER REFERENCES teams(team_id),
    match_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (team_one_id, team_two_id, match_date)
);

CREATE TABLE ladder (
    ladder_id SERIAL PRIMARY KEY,
    team_id INTEGER REFERENCES teams(team_id),
    rank INTEGER,
    points INTEGER,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE FUNCTION update_ladder_rank()
RETURNS TRIGGER AS $$
DECLARE
    winning_team_id INTEGER;
    losing_team_id INTEGER;
    win_rank INTEGER;
    lose_rank INTEGER;
    rank_difference INTEGER;
    new_rank INTEGER;
BEGIN
    -- Determine the winning and losing team IDs from the match results
    winning_team_id := NEW.winner;
    IF NEW.team_one_id = winning_team_id THEN
        losing_team_id := NEW.team_two_id;
    ELSE
        losing_team_id := NEW.team_one_id;
    END IF;

    -- Retrieve the current ranks for both the winning and losing teams from the ladder
    SELECT rank INTO win_rank FROM ladder WHERE team_id = winning_team_id;
    SELECT rank INTO lose_rank FROM ladder WHERE team_id = losing_team_id;

    -- Calculate the difference in ranks between the losing and winning teams
    rank_difference := lose_rank - win_rank;

    -- Execute rank update logic only if the rank difference is exactly 2
    IF rank_difference = 2 THEN
        -- Calculate new rank for the winning team
        new_rank := win_rank + 2;

        -- Shift down other teams
        UPDATE ladder
        SET rank = rank + 1
        WHERE rank < win_rank AND rank >= new_rank;

        -- Move the winning team up by 2 places
        UPDATE ladder
        SET rank = new_rank
        WHERE team_id = winning_team_id;
    END IF;

    IF rank_difference = 1 THEN
        -- Calculate new rank for the winning team
        new_rank := win_rank + 1;

        -- Shift down other teams
        UPDATE ladder
        SET rank = rank + 1
        WHERE rank < win_rank AND rank >= new_rank;

        -- Move the winning team up by 2 places
        UPDATE ladder
        SET rank = new_rank
        WHERE team_id = winning_team_id;
    END IF;

    IF rank_difference = 0 THEN
        -- Calculate new rank for the winning team
        new_rank := win_rank + 1;

        -- Shift down other teams
        UPDATE ladder
        SET rank = rank + 1
        WHERE rank < win_rank AND rank >= new_rank;

        -- Move the winning team up by 2 places
        UPDATE ladder
        SET rank = new_rank
        WHERE team_id = winning_team_id;
    END IF;

    -- Return the NEW record from the trigger function
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_ladder_after_match
AFTER INSERT OR UPDATE OF winner ON matches
FOR EACH ROW
EXECUTE FUNCTION update_ladder_rank();

ALTER TABLE teams OWNER TO ladder;
ALTER TABLE players OWNER TO ladder;
ALTER TABLE matches OWNER TO ladder;
ALTER TABLE ladder OWNER TO ladder;
EOSQL