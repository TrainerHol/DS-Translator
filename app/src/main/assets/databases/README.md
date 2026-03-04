# JMdict Dictionary Database

Place the pre-built `jmdict.db` SQLite database file in this directory.

## Expected Schema

The database should contain two tables:

### `entries` table
| Column   | Type    | Description                    |
|----------|---------|--------------------------------|
| ent_seq  | INTEGER | Primary key (JMdict entry ID)  |
| kanji    | TEXT    | JSON array of kanji writings   |
| kana     | TEXT    | JSON array of kana readings    |

### `senses` table
| Column   | Type    | Description                           |
|----------|---------|---------------------------------------|
| id       | INTEGER | Primary key (auto-increment)          |
| ent_seq  | INTEGER | Foreign key to entries.ent_seq        |
| glosses  | TEXT    | JSON array of English definitions     |
| pos      | TEXT    | JSON array of part-of-speech tags     |
| jlpt     | INTEGER | JLPT level (5=N5, 4=N4, ..., 1=N1)   |

## Building the Database

The database can be built from the JMdict XML data:
1. Download JMdict_e.gz from https://www.edrdg.org/wiki/index.php/JMdict-EDICT_Dictionary_Project
2. Parse entries and senses into the schema above
3. Merge JLPT level data from community vocabulary lists
4. Place the resulting `jmdict.db` file in this directory

The app loads this database via Room's `createFromAsset("databases/jmdict.db")`.
