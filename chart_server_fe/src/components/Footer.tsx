import '../App.css'
import "bootstrap-icons/font/bootstrap-icons.css";

export default function Footer() {
    return (
        <div className="Footer Wrap bg-secondary">
            <div className="container">
                <p className="text-white">© {new Date().getFullYear()} Njord OpenENC Authors</p>
                <a href="https://github.com/manimaul/njord" className="text-white bi-github"> Github</a>
            </div>
        </div>
    )
}