package io.madrona.njord.db

import DataSource
import io.madrona.njord.Singletons

class NukeDao(
    ds: DataSource = Singletons.ds,
) : Dao(ds) {

    suspend fun truncateAllAsync(): Boolean = sqlOpAsync { conn ->
        conn.statement("TRUNCATE TABLE features, charts, base_features RESTART IDENTITY CASCADE;").execute()
        true
    } ?: false
}
