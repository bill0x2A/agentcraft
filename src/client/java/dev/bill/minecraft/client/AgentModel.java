package dev.bill.minecraft.client;

import dev.bill.minecraft.AgentActivity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Original cuboid models with a small, reproducible palette texture atlas. */
final class AgentModel extends EntityModel<AgentRenderState> {
    private final ModelPart rig, head, leftArm, rightArm, leftEye, rightEye, indicator, keyboard;
    private final boolean claude;

    AgentModel(ModelPart root, boolean claude) {
        super(root); this.claude=claude;
        rig=root.getChild("rig"); head=rig.getChild("head");
        leftArm=rig.getChild("left_arm"); rightArm=rig.getChild("right_arm");
        leftEye=head.getChild("left_eye"); rightEye=head.getChild("right_eye");
        indicator=rig.getChild("indicator"); keyboard=rig.getChild("keyboard");
    }

    private static CubeListBuilder box(int color,float x,float y,float z,float w,float h,float d) {
        return CubeListBuilder.create().texOffs((color%4)*64,(color/4)*64).addBox(x,y,z,w,h,d);
    }
    private static PartDefinition part(PartDefinition parent,String name,int color,float x,float y,float z,float w,float h,float d, float px,float py,float pz) {
        return parent.addOrReplaceChild(name,box(color,x,y,z,w,h,d),PartPose.offset(px,py,pz));
    }

    static LayerDefinition layer(boolean claude) {
        MeshDefinition mesh=new MeshDefinition();
        PartDefinition rig=mesh.getRoot().addOrReplaceChild("rig",CubeListBuilder.create(),PartPose.ZERO);
        PartDefinition head;
        if(claude) {
            PartDefinition body=part(rig,"body",0,-7,-5,-4.5F,14,9,9,0,16,0);
            part(body,"shell_top",1,-6,-5.4F,-4,12,1,8,0,0,0);
            part(body,"mouth",2,-2,0,-4.9F,4,1,.5F,0,0,0);
            head=rig.addOrReplaceChild("head",CubeListBuilder.create(),PartPose.offset(0,10,0));
            for(int side=-1;side<=1;side+=2) {
                String prefix=side<0?"left":"right";
                part(head,prefix+"_stalk",0,-.65F,-3,0,1.3F,4,1.3F,side*4,-1,-3);
                part(head,prefix+"_eye_white",4,-1.7F,-1.7F,-1,3.4F,3.4F,2.5F,side*4,-4,-3);
                part(head,prefix+"_eye",3,-.85F,-1,-.4F,1.7F,2,.6F,side*4,-4,-4);
                PartDefinition arm=part(rig,prefix+"_arm",0,-1.3F,0,-1.3F,2.6F,5,2.6F,side*9,12,-1);
                part(arm,"claw",0,-3,3,-3,6,4,5,0,0,0);
                part(arm,"pincer_a",1,-3,2,-6,2,5,3,0,0,0);
                part(arm,"pincer_b",1,1,2,-6,2,5,3,0,0,0);
                for(int i=0;i<3;i++) {
                    PartDefinition leg=part(rig,prefix+"_leg_"+i,0,side<0?-4:0,0,-1,4,2,2,side*6,20,i*3-3);
                    part(leg,"foot",1,side<0?-4:-1,1,-1.2F,2,3,2.4F,0,0,0);
                }
            }
            rig.addOrReplaceChild("keyboard",CubeListBuilder.create(),PartPose.ZERO);
        } else {
            head=part(rig,"head",0,-6,-8,-5,12,10,10,0,2,0);
            part(head,"visor",2,-5,-5,-5.4F,10,5,.5F,0,0,0);
            part(head,"left_eye",3,-.9F,-1,-.3F,1.8F,2,.5F,-2.3F,-2.8F,-5.6F);
            part(head,"right_eye",3,-.9F,-1,-.3F,1.8F,2,.5F,2.3F,-2.8F,-5.6F);
            part(head,"antenna",5,-.5F,-11,-.5F,1,3,1,0,0,0);
            part(head,"antenna_light",3,-1,-12,-1,2,1.4F,2,0,0,0);
            PartDefinition body=part(rig,"body",0,-5,-5,-3,10,10,6,0,9,0);
            part(body,"chest",1,-3,-3,-3.5F,6,4,.6F,0,0,0);
            part(body,"status_light",3,-.6F,-2,-4,1.2F,1.2F,.6F,0,0,0);
            part(body,"waist",2,-4,5,-2.5F,8,2,5,0,0,0);
            for(int side=-1;side<=1;side+=2) {
                String prefix=side<0?"left":"right";
                PartDefinition arm=part(rig,prefix+"_arm",0,-1.5F,0,-1.5F,3,9,3,side*7,5,0);
                part(arm,"cuff",1,-1.6F,6,-1.6F,3.2F,3,3.2F,0,0,0);
                part(arm,"hand",2,-1.4F,9,-1.4F,2.8F,1.5F,2.8F,0,0,0);
                PartDefinition leg=part(rig,prefix+"_leg",5,-1.5F,0,-2,3,7,4,side*3,16,0);
                part(leg,"foot",0,-2,5,-3,4,3,6,0,0,0);
            }
            PartDefinition keyboard=part(rig,"keyboard",2,-6,0,-2.5F,12,1,5,0,13,-8);
            for(int x=0;x<4;x++)for(int z=0;z<2;z++)part(keyboard,"key_"+x+"_"+z,1,-4.5F+x*2.6F,-.3F,-1.5F+z*2,1.8F,.4F,1.2F,0,0,0);
        }
        PartDefinition indicator=part(rig,"indicator",7,-1,0,-1,2,6,2,claude?12:10,claude?2:-6,-1);
        part(indicator,"dot",7,-1,8,-1,2,2,2,0,0,0);
        return LayerDefinition.create(mesh,256,128);
    }

    @Override public void setupAnim(AgentRenderState state) {
        super.setupAnim(state);
        float t=state.ageInTicks;
        indicator.visible=state.activity==AgentActivity.ATTENTION;
        keyboard.visible=!claude && state.activity==AgentActivity.WORKING;
        head.yRot=(float)Math.toRadians(state.yRot)*.35F;
        leftEye.yScale=rightEye.yScale=1;
        switch(state.activity) {
            case RESTING -> {
                rig.y=(float)Math.sin(t*.055)*.35F;
                head.xRot=.12F;
                leftArm.zRot=claude?-.2F:.08F; rightArm.zRot=-leftArm.zRot;
                leftEye.yScale=rightEye.yScale=t%100>94?.08F:.45F;
            }
            case WORKING -> {
                rig.y=(float)Math.sin(t*.3)*.15F;
                head.xRot=.2F;
                leftArm.xRot=(claude?-.65F:-.9F)+(float)Math.sin(t*.65)*.22F;
                rightArm.xRot=(claude?-.65F:-.9F)-(float)Math.sin(t*.65)*.22F;
                leftArm.zRot=claude?-.35F:-.18F; rightArm.zRot=-leftArm.zRot;
            }
            case ATTENTION -> {
                rig.y=-(float)Math.abs(Math.sin(t*.15))*.6F;
                leftArm.xRot=-2.1F+(float)Math.sin(t*.3)*.18F;
                rightArm.xRot=claude?-2.1F:-.35F;
                leftArm.zRot=claude?-.65F:-.35F; rightArm.zRot=claude?.65F:.15F;
                indicator.zRot=(float)Math.sin(t*.15)*.12F;
                indicator.yScale=1+(float)Math.sin(t*.2)*.06F;
            }
            case OFFLINE -> { head.xRot=.4F; rig.y=.5F; leftEye.yScale=rightEye.yScale=.08F; }
            case UNKNOWN -> { head.zRot=.1F; leftEye.yScale=rightEye.yScale=.7F; }
        }
        if (state.seated) {
            rig.y += claude ? 2 : 8;
            if (!claude) {
                rig.getChild("left_leg").xRot = -1.45F;
                rig.getChild("right_leg").xRot = -1.45F;
            } else {
                for (String side : new String[]{"left", "right"}) for (int i=0; i<3; i++)
                    rig.getChild(side+"_leg_"+i).zRot = side.equals("left") ? -.35F : .35F;
            }
        }
    }
}
