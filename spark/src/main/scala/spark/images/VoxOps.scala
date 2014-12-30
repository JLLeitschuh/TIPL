package spark.images

import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import tipl.spark.{DSImg, KVImg}
import tipl.tools.BaseTIPLPluginIn
import tipl.util.{TImgTools, D3int}
import tipl.util.TIPLOps._

import scala.reflect.ClassTag


object VoxOps {


  /**
   * A very generic voxel operation taking a list of input voxels and producing an output voxel
   * @tparam A the type of the input image
   * @tparam B the type of the output image
   */
  trait VoxOp[A,B] extends Serializable {
    val atag: ClassTag[A]
    val btag: ClassTag[B]
    def neededVoxels(pos: D3int): Seq[D3int]
    def process(curvox: (D3int,A),voxs: Seq[(D3int,A)]): Seq[(D3int,B)]
  }

  trait ArrayVoxOp[A,B] extends Serializable {
    val atag: ClassTag[A]
    val btag: ClassTag[B]
    def neededVoxels(): Array[D3int]
    def process(curvox: (D3int,A),voxs: Array[A]): Seq[(D3int,B)]
  }

  /**
   * Converts an array operation to a standard operation
   * @param avo the array operation to replace
   * @param defVal the value to use around the border (if a value is missing)
   * @tparam A the type of the input
   * @tparam B the type of the output
   */
  class ArrayVoxOpToVoxOp[A,B](avo: ArrayVoxOp[A,B], defVal: A)(
                                       implicit val atag: ClassTag[A],
                                                val btag: ClassTag[B]) extends stationaryVoxOp[A,B]
  {

    override def relVoxels: Seq[D3int] = avo.neededVoxels()
    override def process(curvox: (D3int, A), voxs: Seq[(D3int, A)]): Seq[(D3int, B)] = {
      val vmap = voxs.toMap
      avo.process(curvox,relVoxels.map(vmap.getOrElse(_,defVal)).toArray)
    }
  }

  trait stationaryVoxOp[A,B] extends VoxOp[A,B] {
    def relVoxels: Seq[D3int]
    override def neededVoxels(pos: D3int): Seq[D3int] = {
      relVoxels.map(_+pos)
    }
  }

  trait neighborhoodArrayVoxOp[A,B] extends ArrayVoxOp[A,B] {
    def neighborSize: D3int

    def isInside(a: D3int,b: D3int): Boolean
    lazy val nvList = {
      val lseq = for(z<- -neighborSize.gz to neighborSize.gz;
                     y<- -neighborSize.gy to neighborSize.gy;
                     x<- -neighborSize.gx  to neighborSize.gx;
                     if isInside(new D3int(0), new D3int(x,y,z)))
      yield new D3int(x,y,z)
      lseq.toArray
    }

    override def neededVoxels() = nvList

  }



  trait neighborhoodVoxOp[A,B] extends stationaryVoxOp[A,B] {
    def neighborSize: D3int

    def isInside(a: D3int,b: D3int): Boolean
    lazy val nvList = {
      for(z<- -neighborSize.gz to neighborSize.gz;
                     y<- -neighborSize.gy to neighborSize.gy;
                     x<- -neighborSize.gx  to neighborSize.gx;
                     if isInside(new D3int(0), new D3int(x,y,z)))
      yield new D3int(x,y,z)
    }
    def relVoxels: Seq[D3int] = nvList
  }

  trait voxelNeighborFilter[A] extends neighborhoodVoxOp[A,Double] {
    implicit val tm: Numeric[A]

    def kernelFactory(): BaseTIPLPluginIn.filterKernel

    override def process(curvox: (D3int, A), voxs: Seq[(D3int, A)]): Seq[(D3int, Double)] = {
      val kernel = kernelFactory()
      for(cPt <- voxs) kernel.addpt(
        curvox._1.gx,cPt._1.gx,
        curvox._1.gy,cPt._1.gy,
        curvox._1.gz,cPt._1.gz,tm.toDouble(cPt._2))
      Seq((curvox._1,kernel.value()))
    }
  }

  abstract class VoxelFilter[A](implicit val tm: Numeric[A],
                          val atag: ClassTag[A],
                          val btag: ClassTag[Double]) extends voxelNeighborFilter[A]


  // voxel implementations

  trait canApplyVoxOp[A] extends Serializable {
    def apply[B](vo: VoxOp[A,B]): canApplyVoxOp[B]
  }

  class listVoxOpImp[A](inList: Seq[(D3int,A)]) extends canApplyVoxOp[A] {
    override def apply[B](vo: VoxOp[A,B]): canApplyVoxOp[B] = {
      val oList = for(cVox <- inList;
          nPos = vo.neededVoxels(cVox._1);
          nVox = inList.filter(p => nPos.contains(p._1))
          )
        yield vo.process(cVox,nVox)
      new listVoxOpImp[B](oList.flatten)
    }
  }


  implicit class rddVoxOpImp[A](inRdd: RDD[(D3int,A)])(implicit val atag: ClassTag[A]) extends
  canApplyVoxOp[A] {
    def getBaseImg() = inRdd
    override def apply[B](vo: VoxOp[A, B]): rddVoxOpImp[B] =
      vo match {
      case nvo: neighborhoodVoxOp[A,B] => nvapply(nvo)
      case _ => sapply(vo)
      }
    def nvapply[B](nvo: neighborhoodVoxOp[A, B]): rddVoxOpImp[B] = {
      val bImg = inRdd.flatMap(
        curVox =>
          nvo.relVoxels.map(offset => (curVox._1-offset,curVox))
      ).groupByKey
      val cImg = bImg.map {
        inPts =>
          val (cPos, allPts) = inPts
          allPts.filter(_._1.isEqual(cPos)).headOption match {
            case Some(cPt) => Some((cPt,allPts))
            case None => None
          }
      }.filter(_.isDefined).map(_.get)
      val dImg = cImg.flatMap{
        cVals =>
          val (cVox,nVox) = cVals
          nvo.process(cVox,nVox.toSeq)
      }
      new rddVoxOpImp(dImg)(nvo.btag)
    }
    def sapply[B](vo: VoxOp[A, B]): rddVoxOpImp[B] = {
      val fImg = inRdd.flatMap(
        cVox => vo.neededVoxels(cVox._1).map((_,cVox))
      )
      // rearrange pixels
      val cImg = fImg.leftOuterJoin(inRdd).
        filter(_._2._2.isDefined).
        map {
        cPt =>
          val (nPos, (cVox, cVal)) = cPt
          (cVox, (nPos,cVal.get))
      }.groupByKey
      val dImg = cImg.flatMap{
        cVals =>
          val (cVox,nVox) = cVals
          vo.process(cVox,nVox.toSeq)
      }
      new rddVoxOpImp(dImg)(vo.btag)
    }
  }

  implicit class kvImgVoxOpImp[A](ikv: KVImg[A])(implicit am: ClassTag[A]) extends
  rddVoxOpImp[A](ikv.getBaseImg()) {
    def apply[B](vo: VoxOp[A, B],outType: Int = TImgTools.IMAGETYPE_UNKNOWN):
    kvImgVoxOpImp[B] = {
      val newRdd = super.apply(vo).getBaseImg()
      new kvImgVoxOpImp[B](KVImg.fromRDD(ikv,outType,newRdd)(vo.btag))(vo.btag)
    }
  }

  implicit class dsImgVoxOpImp[A](ids: DSImg[A])(implicit am: ClassTag[A]) extends Serializable {

    def apply[B](vo: VoxOp[A, B],outType: Int = TImgTools.IMAGETYPE_UNKNOWN)= vo match {
      case nvo: neighborhoodVoxOp[A,B] => nvapply(nvo)
      case _ => kvapply(DSImg.toKVImg(ids),vo,ids.getImageType)
    }
    def kvapply[B](ikv: KVImg[A],vo: VoxOp[A,B], imgType: Int) =
      ikv(vo,imgType)

    def nvapply[B](nvo: neighborhoodVoxOp[A,B]): dsImgVoxOpImp[B] = ??? //TODO implement slice
    // based method
  }

  trait canApplyArrayVoxOp[A] extends Serializable {
    def apply[B](vo: ArrayVoxOp[A,B], paddingValue: A): canApplyArrayVoxOp[B]
  }

  class listArrVoxOpImp[A](inList: Seq[(D3int,A)]) extends canApplyArrayVoxOp[A] {
    override def apply[B](avo: ArrayVoxOp[A,B], paddingValue: A): listArrVoxOpImp[B] = {
      val outVox = avo.neededVoxels()
      val pointMap = inList.toMap
      val oList = for(cVox <- inList;
                      voxs = outVox.map(pointMap.getOrElse(_,paddingValue)).toArray(avo.atag))
        yield avo.process(cVox,voxs)
      new listArrVoxOpImp[B](oList.flatten)
    }
  }

  class rddArrVoxOpImp[A](inRdd: RDD[(D3int,A)]) extends canApplyArrayVoxOp[A] {
    override def apply[B](avo: ArrayVoxOp[A,B], paddingValue: A): rddArrVoxOpImp[B] = {
      val outVox = avo.neededVoxels()
      val bImg = inRdd.flatMap(
        curVox =>
          outVox.map(offset => (curVox._1-offset,curVox))
      ).groupByKey

      val cImg = bImg.map {
        inPts =>
          val (cPos, allPts) = inPts
          allPts.filter(_._1.isEqual(cPos)).headOption match {
            case Some(cPt) => Some((cPt,allPts))
            case None => None
          }
      }.filter(_.isDefined).map(_.get)
      val fImg = cImg.mapValues{
        inVals =>
          val curMap = inVals.toMap
          outVox.map(curMap.getOrElse(_,paddingValue)).toArray(avo.atag)
      }.flatMap{
        inKV =>
          avo.process(inKV._1,inKV._2)
      }

      new rddArrVoxOpImp[B](fImg)
    }


  }



}
