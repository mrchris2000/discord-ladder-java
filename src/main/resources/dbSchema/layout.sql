#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
        CREATE USER ladder;
        ALTER USER ladder PASSWORD 'discPwd#!';
        CREATE DATABASE discLadder;
        GRANT ALL PRIVILEGES ON DATABASE discLadder TO ladder;

 \c discladder;
CREATE TABLE players (
    player_id SERIAL PRIMARY KEY,
    player_name VARCHAR(100) UNIQUE NOT NULL,
    fafName VARCHAR(100) UNIQUE,
    active BOOLEAN,
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
    team_one_score INTEGER,
    team_two_score INTEGER,
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
ALTER TABLE teams OWNER TO ladder;
ALTER TABLE players OWNER TO ladder;
ALTER TABLE matches OWNER TO ladder;
ALTER TABLE ladder OWNER TO ladder;

EOSQL