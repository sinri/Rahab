package io.github.sinri.Rahab.test.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ByteComputeTest {
    public static void main(String[] args) {
        byte[] listA=new byte[100];
        new Random().nextBytes(listA);

        List<Byte> listB=new ArrayList<>();
        List<Byte> listC=new ArrayList<>();

        byte delta=0;
//        System.out.println("b\tx\t"+delta);
        for(var b:listA){
            byte x= (byte) (b+delta);
            delta=x;
            //System.out.println(b+"\t"+x+"\t"+delta);
            listB.add(x);
        }

        byte delta2=0;
        for (var x : listB) {
            delta2=(byte)(x-delta2);
            //System.out.println(delta2);
            listC.add(delta2);
            delta2=x;
        }

        for(var i=0;i<100;i++){
            boolean same=listA[i]==listC.get(i);
            String content="#"+i+"\t"+listA[i]+"\t"+listB.get(i)+"\t"+listC.get(i)+"\t| "+same;
            if(same) System.out.println(content);
            else System.err.println(content);
        }
    }
}
