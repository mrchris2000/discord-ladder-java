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
    logo TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE teams (
    team_id SERIAL PRIMARY KEY,
    player_one_id INTEGER REFERENCES players(player_id),
    player_two_id INTEGER REFERENCES players(player_id),
    team_name VARCHAR(100),
    team_description TEXT,
    logo TEXT,
    other TEXT,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (player_one_id, player_two_id)
);

CREATE TABLE matches (
    match_id SERIAL PRIMARY KEY,
    replay_id INTEGER DEFAULT 0,
    team_one_id INTEGER REFERENCES teams(team_id),
    team_two_id INTEGER REFERENCES teams(team_id),
    winner INTEGER REFERENCES teams(team_id),
    match_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    other TEXT,
    UNIQUE (team_one_id, team_two_id, match_date)
);

CREATE TABLE ladder (
    ladder_id SERIAL PRIMARY KEY,
    team_id INTEGER REFERENCES teams(team_id),
    rank INTEGER,
    points INTEGER,
    logo TEXT,
    active BOOLEAN DEFAULT TRUE,
    other TEXT,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE teams OWNER TO ladder;
ALTER TABLE players OWNER TO ladder;
ALTER TABLE matches OWNER TO ladder;
ALTER TABLE ladder OWNER TO ladder;
EOSQL