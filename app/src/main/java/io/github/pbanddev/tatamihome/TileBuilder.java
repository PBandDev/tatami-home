package io.github.pbanddev.tatamihome;

/** Makes the static layout of one tile: its rows, joints and steps. Everything that moves is the plan's. */
interface TileBuilder {
    TileLayout build(Look.Plan plan);
}
