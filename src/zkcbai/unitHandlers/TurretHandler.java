/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import zkcbai.Command;
import zkcbai.UpdateListener;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.AttackGroundTask;
import zkcbai.unitHandlers.units.tasks.Task;
import zkcbai.unitHandlers.units.tasks.WaitTask;

/**
 *
 * @author User
 */
public class TurretHandler extends UnitHandler implements UpdateListener {

    Random rnd = new Random();
    AIUnit delayLLT = null;
    Map<List<Float>, Integer> attackCmds = new HashMap();
    AIFloat3 partyBeacon = new AIFloat3();
    float baseHeight;

    public TurretHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        cmd.addUpdateListener(this);
        partyBeacon.x = clbk.getMap().getWidth() * 4;
        partyBeacon.z = clbk.getMap().getHeight() * 4;
        partyBeacon.y = clbk.getMap().getElevationAt(partyBeacon.x, partyBeacon.z) + 400;
        baseHeight = clbk.getMap().getElevationAt(partyBeacon.x, partyBeacon.z) + 400;
    }

    @Override
    public AIUnit addUnit(Unit u) {
        aiunits.put(u.getUnitId(), new AIUnit(u, this));
        troopIdle(aiunits.get(u.getUnitId()));
        return aiunits.get(u.getUnitId());
    }

    @Override
    public void removeUnit(AIUnit u) {
        aiunits.remove(u.hashCode());
    }

    Map<AIUnit, AIFloat3> lastTarget = new HashMap();

    @Override
    public void troopIdle(AIUnit u) {
        if (!clbk.getEnemyUnitsIn(u.getPos(), u.getMaxRange() * 1.3f).isEmpty()) {
            u.assignTask(new WaitTask(command.getCurrentFrame() + 30, this));
            return;
        }
        AIFloat3 target = null;
        AIFloat3 unclippedtarget = null;
        boolean party = false;
        float freq = 1f;
        if (u.getDef().getName().equals("corllt")) {
            freq = 30f;
            if (!u.equals(delayLLT) && command.areaManager.getCommandsPerSecond() > 10 + 20 * u.getUnit().getUnitId() / AIUnit.getMaxUnitId()) {
                freq = 10f;
                //party = true;
            }
        } else if (u.getDef().getName().equals("armdeva")) {
            freq = 0.3f;
        /*} else if (u.getDef().getName().equals("corhlt")) {
            freq = 0.2f;
            party = true;*/
        } else if (u.getDef().getName().equals("armartic")) {
            freq = 0.3f;
        } else {
            u.assignTask(new WaitTask(command.getCurrentFrame() + 300, this));
            return;
        }
        freq /= 3; //normalize to 1 second = 1 freq
        boolean valid = false;
        List<Unit> nearbyFriendlies = clbk.getFriendlyUnitsIn(u.getPos(), u.getMaxRange() * 1.5f);
        int tries = 0;
        while (!valid && tries++ < 15) {
            if (!lastTarget.containsKey(u) || tries > 12) {
                float x = rnd.nextFloat() * u.getMaxRange() * 2 - u.getMaxRange();
                float z = rnd.nextFloat() * u.getMaxRange() * 2 - u.getMaxRange();
                target = new AIFloat3(x, 0, z);
                target.add(u.getPos());
            } else {
                float x = lastTarget.get(u).x;
                float z = lastTarget.get(u).z;
                float dist = (float) Math.sqrt((x - u.getPos().x) * (x - u.getPos().x) + (z - u.getPos().z) * (z - u.getPos().z));
                float distVar = 0.3f;
                dist = Math.max(u.getMaxRange() * (1 - distVar), dist + (rnd.nextFloat() * u.getMaxRange() * distVar - u.getMaxRange() * 0.5f * distVar));
                //dist = 0.3f * u.getMaxRange();
                float arc = (float) Math.atan2(z - u.getPos().z, x - u.getPos().x);
                arc += tries * 0.1f / freq + rnd.nextFloat() / 18;
                if (party) {
                    arc += Math.random() * 10;
                }
                target = new AIFloat3(dist * (float) Math.cos(arc), 0, dist * (float) Math.sin(arc));
                target.add(u.getPos());
            }
            target.y = clbk.getMap().getElevationAt(target.x, target.z);
            unclippedtarget = new AIFloat3(target);
            if (target.x > command.areaManager.getMapWidth()) {
                target.x = command.areaManager.getMapWidth() - 1;
            }
            if (target.x < 0) {
                target.x = 0;
            }
            if (target.z > command.areaManager.getMapHeight()) {
                target.z = command.areaManager.getMapHeight() - 1;
            }
            if (target.z < 0) {
                target.z = 0;
            }
            if (party) {
                AIFloat3 bpos = new AIFloat3(partyBeacon);
                bpos.sub(u.getPos());
                bpos.normalize();
                bpos.scale(u.getMaxRange() * 0.9f);
                bpos.add(u.getPos());
                target = bpos;
                target.y = Math.max(target.y, clbk.getMap().getElevationAt(target.x, target.z));
            }
            valid = true;
            if (u.distanceTo3D(target) > u.getMaxRange()) {
                valid = false;
            }
            for (Unit unit : nearbyFriendlies) {
                if (Command.distance2D(target, unit.getPos()) < unit.getDef().getRadius()
                        + (u.getDef().getName().equalsIgnoreCase("armdeva") ? 80 : 5)) {
                    valid = false;
                }
            }
        }
        lastTarget.put(u, unclippedtarget);
        u.assignTask(new AttackGroundTask(target, command.getCurrentFrame() + (party ? -command.getCurrentFrame() % 30 + 30 : (int) Math.ceil(10 / freq * (tries / 10f + 1))), this));
        if (u.equals(delayLLT)) {
            List<Float> floats = new ArrayList();
            floats.add(target.x);
            floats.add(target.y);
            floats.add(target.z);
            attackCmds.put(floats, command.getCurrentFrame() + 1);
        }
        /*
         AIFloat3 offset = new AIFloat3(u.getPos());
         offset.add(new AIFloat3(10,0,10));
         u.patrolTo(offset, -1);*/
    }

    @Override
    public void troopIdle(AISquad s) {
    }

    @Override
    public void abortedTask(Task t) {
        finishedTask(t);
    }

    @Override
    public void finishedTask(Task t) {
        if (t instanceof AttackGroundTask) {
            AttackGroundTask at = (AttackGroundTask) t;
            at.getLastExecutingUnit().getUnits().iterator().next().getUnit().stop((short) 0, command.getCurrentFrame() + 10);
        }
    }

    @Override
    public void reportSpam() {
    }

    @Override
    public void unitDestroyed(AIUnit u, Enemy e) {
        super.unitDestroyed(u, e);
        if (u.equals(delayLLT)) {
            delayLLT = null;
        }
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
    }

    @Override
    public boolean retreatForRepairs(AITroop u) {
        return false;
    }

    @Override
    public void update(int frame) {

        //partyBeacon.y = baseHeight + 500 * ((float)Math.sin(frame * 2 * Math.PI / 60d) + 1);
        if (frame % 30 == 0) {
            partyBeacon.x = (float) Math.random() * clbk.getMap().getWidth() * 8;
            partyBeacon.z = (float) Math.random() * clbk.getMap().getHeight() * 8;
            partyBeacon.y = 1000;
            Enemy best = null;
            for (Enemy e : command.getEnemyUnits(false)){
                if (e.getMetalCost() > 1500  && (best == null || best.getMetalCost() < e.getMetalCost())){
                    best = e;
                    partyBeacon = e.getPos();
                }
            }
            
        }
        if (delayLLT == null) {
            for (AIUnit au : getUnits()) {
                if (au.getDef().getName().equalsIgnoreCase("corllt")) {
                    delayLLT = au;
                }
            }
        }
        if (delayLLT != null && !delayLLT.getUnit().getCurrentCommands().isEmpty()) {
            if (attackCmds.containsKey(delayLLT.getUnit().getCurrentCommands().get(0).getParams())) {
                command.setCommandDelay(frame - attackCmds.get(delayLLT.getUnit().getCurrentCommands().get(0).getParams()));
            }
            if (attackCmds.size() > 1000) {
                attackCmds = new HashMap();
            }
        }
    }

}
