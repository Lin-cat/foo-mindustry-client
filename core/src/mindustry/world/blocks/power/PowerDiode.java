package mindustry.world.blocks.power;

import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.annotations.Annotations.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.meta.*;

public class PowerDiode extends Block{
    public @Load("@-arrow") TextureRegion arrow;

    public PowerDiode(String name){
        super(name);
        rotate = true;
        update = true;
        solid = true;
        insulated = true;
        group = BlockGroup.power;
        noUpdateDisabled = true;
        schematicPriority = 10;
        envEnabled |= Env.space;
    }

    @Override
    public void setBars(){
        super.setBars();

        addBar("back", entity -> new Bar("bar.input", Pal.powerBar, () -> bar(entity.back())));
        addBar("front", entity -> new Bar("bar.output", Pal.powerBar, () -> bar(entity.front())));
    }

    @Override
    public void drawPlanRegion(BuildPlan plan, Eachable<BuildPlan> list){
        Draw.rect(fullIcon, plan.drawx(), plan.drawy());
        Draw.rect(arrow, plan.drawx(), plan.drawy(), !rotate ? 0 : plan.rotation * 90);
    }

    // battery % of the graph on either side, defaults to zero
    public float bar(Building tile){
        return (tile != null && tile.block.hasPower) ? tile.power.graph.getLastPowerStored() / tile.power.graph.getTotalBatteryCapacity() : 0f;
    }

    /** Returns a set of int pairs, pairs are stored as minID, maxID. */
    public static @Nullable Seq<int[]> connections(Team team) {
        var out = new Seq<int[]>();
        // FINISHME: This is horrid
        team.data().buildings.each(b -> b instanceof PowerDiodeBuild && b.tile != null && b.front() != null && b.back() != null && b.back().block.hasPower && b.front().block.hasPower && b.back().team == b.front().team, b -> {
            PowerGraph backGraph = b.back().power.graph;
            PowerGraph frontGraph = b.front().power.graph;
            if (backGraph == frontGraph) return;

            out.add(new int[]{Math.min(frontGraph.getID(), backGraph.getID()), Math.max(frontGraph.getID(), backGraph.getID())});
        });
        return out;
    }

    public class PowerDiodeBuild extends Building{
        public WindowedMean transferred = new WindowedMean(60);

        @Override
        public void draw(){
            Draw.rect(region, x, y, 0);
            Draw.rect(arrow, x, y, rotate ? rotdeg() : 0);
        }

        @Override
        public void updateTile(){
            super.updateTile();

            if(tile == null || front() == null || back() == null || !back().block.hasPower || !front().block.hasPower || back().team != team || front().team != team) return;

            PowerGraph backGraph = back().power.graph;
            PowerGraph frontGraph = front().power.graph;
            if(backGraph == frontGraph) return;

            // 0f - 1f of battery capacity in use
            float backStored = backGraph.getBatteryStored() / backGraph.getTotalBatteryCapacity();
            float frontStored = frontGraph.getBatteryStored() / frontGraph.getTotalBatteryCapacity();

            // try to send if the back side has more % capacity stored than the front side
            if(backStored > frontStored){
                // send half of the difference
                float amount = backGraph.getBatteryStored() * (backStored - frontStored) / 2;
                // prevent sending more than the front can handle
                amount = Mathf.clamp(amount, 0, frontGraph.getTotalBatteryCapacity() * (1 - frontStored));
                transferred.add(amount);

                backGraph.transferPower(-amount);
                frontGraph.transferPower(amount);
            }
        }
    }
}
