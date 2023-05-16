import '../App.css'
import apache_2_badge from '../apache_2_badge.svg'
import github from '../github.svg'

export default function Footer() {
    return (
        <div className="Footer Wrap bg-secondary">
            <div className="container">
                <p className="text-white">
                    © {new Date().getFullYear()} <a className="text-white" href="https://github.com/manimaul/njord/commits">Njord OpenENC Authors</a>
                </p>
                <a className="text-white" href="https://github.com/manimaul/njord">
                    <img style={{width:25, height:25}} src={github} />
                </a> <a className="text-white" href="https://github.com/manimaul/njord/blob/master/LICENSE">
                    <img  src={apache_2_badge} />
                </a>
            </div>
        </div>
    )
}