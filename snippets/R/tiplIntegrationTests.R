# Some basic tests with synthetic images
as.tipl.pf<-function(obj) .jcast(obj,new.class="tipl/formats/PureFImage$PositionFunction",check=T)
tiplPlane<-as.tipl.pf(.jnew("tipl/tests/TestFImages$DiagonalPlaneFunction"))
tiplLine<-as.tipl.pf(.jnew("tipl/tests/TestFImages$LinesFunction"))
tiplDots<-as.tipl.pf(.jnew("tipl/tests/TestFImages$DotsFunction"))
# this doesnt work :: not sure why .jcall("tipl/tests/TestFImages","tipl/formats/TImgRO","wrapIt",as.integer(10),tiplPlane)
tiplWrap<-function(c.fun,size=10) J("tipl.tests.TestFImages")$wrapIt(as.integer(size),c.fun)
planeImg<-tiplWrap(tiplPlane)
lineImg<-tiplWrap(tiplLine)
dotsImg<-tiplWrap(tiplDots)