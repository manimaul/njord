import React from "react";
import Container from 'react-bootstrap/Container';
import Nav from 'react-bootstrap/Nav';
import Navbar from 'react-bootstrap/Navbar';
import {Link} from "react-router-dom";

type NavLinkProps = {
    path: string,
    label: string,
}
function NavLink(props: NavLinkProps) {
    return (
        <li className="nav-item"><Link to={props.path} className="nav-link">{props.label}</Link></li>
    )
}
function NavBar() {
    return (
        <Navbar bg="secondary" expand="lg" variant="dark">
            <Container>
                <Navbar.Brand>Njord</Navbar.Brand>
                <Navbar.Toggle aria-controls="basic-navbar-nav" />
                <Navbar.Collapse id="basic-navbar-nav">
                    <Nav className="me-auto">
                        <NavLink path="/" label="About" />
                        <NavLink path="/enc" label="ENC" />
                        <NavLink path="/control/charts" label="Control Panel" />
                    </Nav>
                </Navbar.Collapse>
            </Container>
        </Navbar>
    );
}

export default NavBar;