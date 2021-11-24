import kotlinx.browser.document
import react.dom.render
import react.dom.*
import kotlinx.css.*
import styled.css
import styled.injectGlobal
import styled.styledDiv

fun main() {

    //https://ktor.io/docs/css-dsl.html#use_css
    val styles = CssBuilder(allowClasses = false).apply {
        body {
            height = 100.vh
            margin(0.px)
        }
    }

    injectGlobal(styles)


    render(document.getElementById("root")) {
        styledDiv {
            css {
                height = 100.vh
                display = Display.flex
                flexDirection = FlexDirection.column
            }
            styledDiv {
                css {
                    flex(0.0, 1.0, FlexBasis.auto)
                }
                h4 {
                    +"Njord - S57 Server"
                }
            }
            styledDiv {
                css {
                    flex(1.0, 1.0, FlexBasis.auto)
                }
                mapLibre()
            }
//            styledDiv {
//                css {
//                    flex(0.0, 1.0, FlexBasis.auto)
//                }
//                h4 {
//                    +"Footer"
//                }
//            }
        }
    }
}

