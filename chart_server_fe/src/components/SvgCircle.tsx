type SvgCircleProps = {
    color: String | undefined
}
export default function SvgCircle(props: SvgCircleProps) {
    return (
        <svg
            xmlns="http://www.w3.org/2000/svg"
            version="1.1"
            width="20"
            height="20"
        >
            <circle cx="10" cy="10" r="10" fill={`${props.color}`} />
        </svg>
    )
}